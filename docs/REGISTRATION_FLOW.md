# OmniHealth — Registration → Tenant Provisioning Flow

> A complete, code-accurate walkthrough of how a new customer goes from **self-service signup** all the way to an **ACTIVE organization with its own physical PostgreSQL database**, plus the runtime machinery that routes each authenticated request to the correct tenant database.
>
> This document is written from the actual source. Every route string, status transition, DTO field, and side effect below matches the code. At the end you'll find a **bottom-to-top reading guide** listing every file in the order you should read it to understand the system.

---

## Table of Contents

1. [The big picture](#1-the-big-picture)
2. [Two databases, two EntityManagerFactories](#2-two-databases-two-entitymanagerfactories)
3. [The onboarding state machine](#3-the-onboarding-state-machine)
4. [End-to-end flow, step by step](#4-end-to-end-flow-step-by-step)
5. [Asynchronous tenant provisioning](#5-asynchronous-tenant-provisioning)
6. [Runtime multi-tenant routing](#6-runtime-multi-tenant-routing)
7. [Authentication & session model](#7-authentication--session-model)
8. [Complete API reference](#8-complete-api-reference)
9. [Response envelope & error model](#9-response-envelope--error-model)
10. [Data model (database schema)](#10-data-model-database-schema)
11. [Configuration & profiles](#11-configuration--profiles)
12. [The series of files — bottom to top](#12-the-series-of-files--bottom-to-top)

---

## 1. The big picture

OmniHealth is a multi-tenant SaaS backend for healthcare organizations. It uses a **database-per-tenant** isolation model:

- A single **platform database** (`omnihealth_platform`) holds all *control-plane* data: users, organizations, memberships, onboarding sessions, plans, subscriptions, and the catalog of provisioned tenant databases.
- Each customer organization gets its **own physical PostgreSQL database** (`omnihealth_tenant_<orgUuidNoDashes>`) that holds that organization's operational healthcare data. This database is **created for real** during onboarding.

The customer journey is a **self-service onboarding flow** modeled as a state machine:

```
signup ─► verify email ─► organization profile ─► provision tenant ─► start trial ─► (payment) ─► activate
```

Two things happen behind the scenes:

1. **`provision-tenant`** kicks off a **background job** that physically runs `CREATE DATABASE`, applies the tenant migrations, and flips catalog rows to `READY`/`COMPLETED`.
2. Once a user is authenticated, a **servlet filter + Hibernate multi-tenancy** transparently route every tenant-scoped query to that user's organization database.

```
                         ┌──────────────────────────────────────────────┐
                         │            PLATFORM DATABASE                   │
   HTTP request          │  users, organizations, memberships,           │
   (session cookie)      │  onboarding sessions, plans, subscriptions,   │
        │                │  tenant_databases (catalog), provisioning_jobs│
        ▼                └──────────────────────────────────────────────┘
 ┌───────────────┐                         ▲
 │ Session filter│  authenticate           │ platform EMF (@Primary, ddl=validate)
 │  → principal  │─────────────────────────┘
 └───────┬───────┘
         │ resolve primary org id
         ▼
 ┌───────────────┐     TenantContext (ThreadLocal = orgId)
 │ Tenant filter │──────────────────────────────┐
 └───────────────┘                               │ tenant EMF (ddl=none, multi-tenant)
                                                  ▼
                         ┌──────────────────────────────────────────────┐
                         │   TENANT DATABASE  omnihealth_tenant_<uuid>    │
                         │   application_metadata, … (per organization)   │
                         └──────────────────────────────────────────────┘
```

---

## 2. Two databases, two EntityManagerFactories

Because there are two logical data sources, Spring Boot's single-EMF auto-configuration is replaced by **two explicitly-declared `EntityManagerFactory` beans**. This is the backbone that makes everything else possible.

| Aspect | Platform EMF | Tenant EMF |
|---|---|---|
| Bean names | `dataSource`, `entityManagerFactory`, `transactionManager` (all `@Primary`) | `tenantEntityManagerFactory`, `tenantTransactionManager` |
| Config class | `config/persistence/PlatformPersistenceConfig.java` | `config/persistence/TenantPersistenceConfig.java` |
| Scans package | `com.omnihealth.platform` | `com.omnihealth.tenant` |
| `hbm2ddl` | `validate` | `none` |
| Multi-tenancy | no | **yes** — a `MultiTenantConnectionProvider<String>` + `CurrentTenantIdentifierResolver<String>` are wired in as instances |
| Flyway | Boot autoconfig runs `db/platform/migration` at startup | programmatic, per tenant DB, runs `db/tenant/migration` during provisioning |

Key rules encoded in the code:

- **Package split is sacred.** `com.omnihealth.platform.tenant.*` = platform-DB *bookkeeping* (the `TenantDatabase` and `ProvisioningJob` catalog entities live here, on the **platform** EMF). `com.omnihealth.tenant.*` = *runtime routing* + tenant-DB application entities (e.g. `ApplicationMetadata`, on the **tenant** EMF). The two `@EnableJpaRepositories` `basePackages` never overlap.
- The tenant EMF uses `hbm2ddl=none` **on purpose**: Hibernate bootstraps it via `getAnyConnection()` (the maintenance DB), which does not contain tenant tables — so schema correctness comes from Flyway, not from Hibernate validation.
- Hibernate 7 note: the old `MultiTenancyStrategy` enum was removed. Multi-tenancy is active *simply because* a `MultiTenantConnectionProvider` is present on the tenant EMF's property map.

---

## 3. The onboarding state machine

Onboarding progress is tracked by a single row in `platform_organization_onboarding`, represented by the `OnboardingSession` entity. Two fields drive it:

- **`status`** — an authoritative `OnboardingStatus` enum value = the *last milestone reached*.
- **`currentStep`** — a free `String` hint = the *next step the client should perform*. (The `OnboardingStep` enum documents the vocabulary; the service writes string literals.)

Because `status` names the past and `currentStep` names the future, they're intentionally "offset" by one.

```
OnboardingStatus:  ACCOUNT_CREATED → EMAIL_VERIFIED → ORGANIZATION_CREATED → TENANT_PROVISIONING
                     → TRIAL_STARTED → PAYMENT_COMPLETED → ACTIVE     (FAILED is the error sink)
```

| # | Action (endpoint) | Requires status | Sets status → | Sets currentStep → |
|---|---|---|---|---|
| 0 | `POST /onboarding/signup` | *(none — creates the session)* | `ACCOUNT_CREATED` | `EMAIL_VERIFICATION` |
| 1 | verify email (`/users/verify-email` **or** `/auth/verify-email`) → `EmailVerifiedEvent` | `ACCOUNT_CREATED` | `EMAIL_VERIFIED` | `ORGANIZATION_PROFILE` |
| 2 | `POST /onboarding/{id}/organization-profile` | `EMAIL_VERIFIED` | `ORGANIZATION_CREATED` | `TENANT_PROVISIONING` |
| 3 | `POST /onboarding/{id}/provision-tenant` | `ORGANIZATION_CREATED` | `TENANT_PROVISIONING` | `PLAN_SELECTION` |
| 4 | `POST /onboarding/{id}/start-trial` | `TENANT_PROVISIONING` | `TRIAL_STARTED` | `PAYMENT` |
| 5 | `POST /onboarding/{id}/payment` | `TRIAL_STARTED` | `PAYMENT_COMPLETED` | `ACTIVATION` |
| 6 | `POST /onboarding/{id}/activate` | `TRIAL_STARTED` **or** `PAYMENT_COMPLETED` | `ACTIVE` | `COMPLETED` |

**Important decoupling:** step 3 advances the *session* status immediately and returns right away, while the actual database creation runs asynchronously. Steps 4–6 gate only on the **session** status, **not** on whether the tenant database is physically `READY`. So a client can walk the state machine to `ACTIVE` while provisioning is still finishing in the background — the two lifecycles are deliberately independent. (The tenant DB readiness is tracked separately in `tenant_databases.status`.)

`requireStatus(...)` guards every transition; a mismatch throws `INVALID_ONBOARDING_STATE` (HTTP 409). A missing session id throws `ONBOARDING_SESSION_NOT_FOUND` (HTTP 404).

---

## 4. End-to-end flow, step by step

### Step 0 — Signup  `POST /api/v1/onboarding/signup`  *(public)*

Orchestrated by `OnboardingServiceImpl.signup(SignupRequest)`. In a single platform transaction it:

1. **Creates the owner user** via `userService.createUser(...)`:
   - maps a `CreateUserRequest` from the signup payload (first/last name, email, password);
   - rejects a duplicate email with `DuplicateResourceException` (HTTP 409);
   - BCrypt-hashes the password;
   - saves the `User` with status `PENDING_EMAIL_VERIFICATION`;
   - creates a `VerificationToken` (raw = random UUID, stored as a **SHA-256 hash**, type `EMAIL_VERIFICATION`, expires in 24h);
   - registers an **after-commit** hook that fires `emailService.sendVerificationEmail(...)` (async) — so the email is only sent if the transaction actually commits.
2. **Creates a draft organization** via `organizationService.createOrganization(req, ownerId)`:
   - `organizationCode` is auto-generated from the display name (`generateOrganizationCode` → slugify + random suffix, retried up to 5× for uniqueness);
   - `legalName` defaults to the display name, `officialEmail` to the signup email;
   - defaults: timezone `Asia/Kolkata`, currency `INR`, locale `en-IN`;
   - organization starts in status `DRAFT`;
   - a `PlatformOrganizationMembership` links the owner as the **primary** member.
3. **Opens the onboarding session** (`OnboardingSession`): status `ACCOUNT_CREATED`, `currentStep = EMAIL_VERIFICATION`, `startedAt = now`, linked to the org + initiating user (via `getReferenceById` to avoid extra selects).

**Response:** `201` · `ApiResponse<OnboardingSessionResponse>` · message *"Signup successful. Please verify your email to continue."*

The verification email links to:
`http://localhost:8080/api/v1/platform/users/verify-email?token=<rawToken>`

---

### Step 1 — Email verification → session advances automatically

There are **two** ways to verify, and both converge on the same event:

- `GET /api/v1/platform/users/verify-email?token=...` — verifies only. `UserServiceImpl.verifyEmail(raw)`:
  hashes the token, loads it, checks it isn't consumed/revoked/expired, stamps `consumedAt`, flips the user to `ACTIVE` + `emailVerifiedAt`, and **publishes `EmailVerifiedEvent(userId)`**.
- `POST /api/v1/platform/auth/verify-email?token=...` — verifies **and logs in** (establishes a session + cookie), returning a `LoginResponse`.

The event is consumed **synchronously, in the same transaction** by `OnboardingEventListener.onEmailVerified` → `OnboardingServiceImpl.onEmailVerified(userId)`, which:

- looks up the session by `initiatedBy = userId` **and** status `ACCOUNT_CREATED`;
- if found, advances it to `EMAIL_VERIFIED`, sets `emailVerifiedAt`, and `currentStep = ORGANIZATION_PROFILE`.

`onEmailVerified` is deliberately **defensive and never throws** — it cannot roll back the verification transaction if no matching session exists.

---

### Step 2 — Organization profile  `POST /api/v1/onboarding/{id}/organization-profile`  *(authenticated)*

`completeOrganizationProfile(UpdateOrganizationRequest)`:

- `requireStatus(EMAIL_VERIFIED)`;
- calls `organizationService.updateOrganization(...)` to fill in the full company profile (legal name, tax IDs, GST/PAN, address, contact, locale, etc. — see the DTO);
- advances the session to `ORGANIZATION_CREATED`, stamps `organizationCompletedAt`, sets `currentStep = TENANT_PROVISIONING`.

---

### Step 3 — Provision tenant  `POST /api/v1/onboarding/{id}/provision-tenant`  *(authenticated)*

`provisionTenant(...)` — this is where the physical database gets requested. See [§5](#5-asynchronous-tenant-provisioning) for the full worker pipeline. Synchronously it:

- `requireStatus(ORGANIZATION_CREATED)`;
- computes `databaseName = "omnihealth_tenant_" + orgId.toString().replace("-", "")`;
- applies **reuse-or-reset** on the `tenant_databases` catalog row (respecting the partial unique index that allows only one active row per org):

  | Existing catalog row | Action |
  |---|---|
  | none | insert a new `PENDING` row → **enqueue** |
  | `READY` | already provisioned → skip |
  | `PENDING` / `PROVISIONING` | in flight → skip |
  | `FAILED` / `ARCHIVED` | reset the same row to `PENDING`, clear timestamps + reason → **enqueue** |

- advances the session to `TENANT_PROVISIONING`, stamps `tenantProvisioningStartedAt`, sets `currentStep = PLAN_SELECTION`;
- if enqueuing: saves a fresh `ProvisioningJob` (status `QUEUED`, `attempts = 0`) and registers an **after-commit** callback that calls the async worker `tenantProvisioningService.provisionAsync(jobId, tenantDatabaseId)`.

**Response is immediate** — the heavy lifting happens in the background after the transaction commits (the worker reloads rows by id, so they must be committed and visible first).

---

### Step 4 — Start trial  `POST /api/v1/onboarding/{id}/start-trial`  *(authenticated)*

`startTrial(...)`:

- `requireStatus(TENANT_PROVISIONING)`;
- loads the default plan via `planRepository.findByCodeAndDeletedAtIsNull("TRIAL")` (seeded by migration V4) — missing → `DEFAULT_TRIAL_PLAN_NOT_FOUND` (500);
- creates a `Subscription` (status `TRIAL`, `createdBy = initiatingUser`), computing the period end as `trialEnd` if it's in the future, else `now + 1 day`;
- advances the session to `TRIAL_STARTED`, sets `currentStep = PAYMENT`.

---

### Step 5 — Payment (optional)  `POST /api/v1/onboarding/{id}/payment`  *(authenticated)*

`completePayment(CompletePaymentRequest)` — body is **optional**:

- `requireStatus(TRIAL_STARTED)`;
- if a body with a non-null `provider` is supplied, records `provider` + `providerReference` onto the organization's active subscription (full billing is deferred to a dedicated module);
- advances the session to `PAYMENT_COMPLETED`, sets `currentStep = ACTIVATION`.

---

### Step 6 — Activate  `POST /api/v1/onboarding/{id}/activate`  *(authenticated)*

`activate(...)`:

- `requireStatus(TRIAL_STARTED, PAYMENT_COMPLETED)` (payment is skippable);
- flips the **organization** to `ACTIVE` + `activatedAt`;
- flips the **session** to `ACTIVE`, stamps `completedAt`, sets `currentStep = COMPLETED`.

Onboarding is complete. The organization is live and (assuming provisioning finished) its tenant database is ready for traffic.

You can inspect progress at any time with `GET /api/v1/onboarding/{id}`.

---

## 5. Asynchronous tenant provisioning

Triggered after-commit from step 3. The moving parts live in `com.omnihealth.platform.tenant.provisioning`:

```
provisionAsync(jobId, tenantDatabaseId)          [TenantProvisioningService, @Async, void]
  │
  ├─ markRunning(jobId, tdbId)                    [TenantProvisioningTxService, own tx]
  │     job → RUNNING, startedAt set, attempts++  ;  tdb → PROVISIONING, provisionedAt set
  │
  ├─ loadTarget(tdbId)                            [TenantProvisioningTxService, read-only]
  │     → TenantTarget(databaseName, schemaName)
  │
  ├─ createDatabaseIfAbsent(databaseName)         [TenantDatabaseAdmin]
  │     validate name ^omnihealth_tenant_[a-z0-9]{32}$
  │     admin datasource, autoCommit=true
  │     SELECT 1 FROM pg_database WHERE datname=?  (guard — no CREATE IF NOT EXISTS in PG)
  │     CREATE DATABASE "<name>"
  │
  ├─ migrate(databaseName, schemaName)            [TenantFlywayMigrator]
  │     Flyway on jdbc:postgresql://host:port/<db>, locations db/tenant/migration,
  │     baselineOnMigrate=true → creates application_metadata + seed row
  │
  └─ markReady(jobId, tdbId)                       [TenantProvisioningTxService, own tx]
        tdb → READY, readyAt set, failure cleared  ;  job → COMPLETED, completedAt set

  on ANY exception:  log.error(...)  +  markFailed(jobId, tdbId, reason)
        tdb → FAILED (+failedAt +failureReason)   ;  job → FAILED (+failedAt +failureReason)
        reason truncated to 4000 chars; not rethrown (void @Async)
```

Design points that matter:

- **Transaction boundaries are split out** into `TenantProvisioningTxService` (all methods `@Transactional("transactionManager")`) so `@Transactional` isn't bypassed by async self-invocation, and so each lifecycle mark commits independently and is visible even if a later step fails.
- **`CREATE DATABASE` runs outside any JPA transaction** on a dedicated **admin datasource** (`tenantAdminDataSource`, `autoCommit=true`) connected to a maintenance DB — you cannot create a database from within a transaction, nor from within the database being created.
- **Retry-safe / idempotent:** the existence-guarded `CREATE` + `baselineOnMigrate` Flyway mean re-running a `FAILED` job (via reuse-or-reset in step 3) converges cleanly.
- **Credentials are never stored** in `tenant_databases` — the catalog holds only host/port/name/schema/pool size. Connection credentials come from `app.tenant.connection.*` config.
- The database name is validated against `^omnihealth_tenant_[a-z0-9]{32}$` before interpolation, because SQL identifiers cannot be bind parameters.

**Catalog state you can watch in SQL:**

```sql
SELECT status, database_name, provisioned_at, ready_at, failed_at, failure_reason
FROM tenant_databases WHERE organization_id = '<orgId>' ORDER BY created_at DESC;
-- expect PENDING → PROVISIONING → READY

SELECT status, attempts, started_at, completed_at, failed_at, failure_reason
FROM platform_provisioning_jobs WHERE organization_id = '<orgId>' ORDER BY created_at DESC;
-- expect QUEUED → RUNNING → COMPLETED (attempts = 1)
```

---

## 6. Runtime multi-tenant routing

Once tenant databases exist, authenticated requests must reach the right one. This is pure Hibernate 7 database-per-tenant multi-tenancy, driven by a `ThreadLocal`.

**Per-request sequence:**

1. `SessionAuthenticationFilter` authenticates the session cookie/bearer token and puts a `PlatformUserPrincipal` in the `SecurityContext`.
2. `TenantContextFilter` (registered right after it) runs: if the principal is a `PlatformUserPrincipal`, it resolves the user's **primary organization id** via `membershipRepository.findPrimaryOrganizationIdByUserId(userId)` and calls `TenantContext.setTenantId(orgId.toString())`. A `finally` block **always** calls `TenantContext.clear()` to prevent ThreadLocal leakage across pooled threads.
3. When a tenant-scoped repository executes, Hibernate asks `TenantIdentifierResolver` for the current tenant → it returns `TenantContext.getTenantId()` or the `__bootstrap__` sentinel if none is set.
4. `TenantConnectionProvider.getConnection(tenantId)`:
   - `null`/`BOOTSTRAP` → hand back a connection from the admin/maintenance datasource (`getAnyConnection()`);
   - otherwise `pools.computeIfAbsent(tenantId, buildTenantPool)` — lazily builds a dedicated `HikariDataSource` for that org.
5. `buildTenantPool(tenantId)`:
   - parses the UUID (invalid → `TenantNotProvisionedException`);
   - looks up the catalog **via `JdbcTemplate`** (not JPA — avoids needing a session during connection acquisition):
     `SELECT database_name, host, port, schema_name, connection_pool_size, status FROM tenant_databases WHERE organization_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1`;
   - if no row or status ≠ `READY` → throw (fail fast);
   - builds a `HikariDataSource` (`jdbc:postgresql://host:port/dbName`, creds from `app.tenant.connection.*`, `poolSize = min(row, cap)`, `minimumIdle = 0`, sets the schema).
   - Pools are cached, rebuilt lazily after a restart from the catalog (source of truth), and closed on `@PreDestroy`.

**Routing proof endpoint:** `POST /api/v1/tenant/ping` (`TenantPingController` → `TenantPingService.ping()`, `@Transactional("tenantTransactionManager")`) inserts and reads back rows in the current tenant's `application_metadata` table and echoes the resolved tenant id. Logging in as users from different organizations and calling `/ping` proves rows land in different physical databases.

---

## 7. Authentication & session model

Stateless, cookie-based custom-token sessions (not Spring's HTTP session):

- **Login** (`AuthServiceImpl.login`) verifies the BCrypt password, enforces a lockout (`MAX_FAILED_ATTEMPTS = 5`, `LOCKOUT_DURATION_MINUTES = 15`), requires the user to be `ACTIVE`, then creates a `PlatformSession` and attaches an HTTP-only cookie.
- **Session tokens** (`SessionServiceImpl`): raw token = `UUID + "-" + UUID`, persisted only as a **SHA-256 hash** in `platform_sessions.refresh_token_hash`. Cookie name `omni_session`, 7-day default / 30-day "remember me". `secure` and `sameSite` come from config (`app.security.cookie.*`). Validation uses a `JOIN FETCH` query (`findActiveByRefreshTokenHashWithUser`) so the user is eagerly loaded — reading a LAZY association inside a security filter would otherwise throw `LazyInitializationException` (filters run outside the OSIV scope).
- **`SessionAuthenticationFilter`** extracts the token (cookie first, then `Authorization: Bearer`), validates it, and — if the user is `ACTIVE` and not deleted — sets a `PlatformUserPrincipal` (authorities: `ROLE_USER`; enabled iff `ACTIVE`; account non-locked iff status ≠ `LOCKED`).
- **`SecurityConfig`**: `STATELESS`, CSRF disabled, CORS default, BCrypt encoder. Custom `AuthenticationEntryPoint` writes a JSON `ApiResponse` with `CommonErrorCode.UNAUTHORIZED`. Filter order: `SessionAuthenticationFilter` **before** `UsernamePasswordAuthenticationFilter`, then `TenantContextFilter` **after** `SessionAuthenticationFilter`.

**Public (permitAll) endpoints:** `POST /platform/users` (+ trailing slash), `/platform/users/verify-email`, `POST /onboarding/signup`, `/platform/auth/login` (+`/**`), `/platform/auth/verify-email` (+`/**`), and Swagger/OpenAPI/`/error`. Everything else requires authentication.

---

## 8. Complete API reference

All routes are built from constants in `common/constants/ApiRoutes.java`:

```
API_V1        = /api/v1
PLATFORM      = /api/v1/platform
AUTH          = /api/v1/platform/auth
ORGANIZATIONS = /api/v1/platform/organizations
USERS         = /api/v1/platform/users
ONBOARDING    = /api/v1/onboarding
TENANT        = /api/v1/tenant
```

Every endpoint returns the standard `ApiResponse<T>` envelope ([§9](#9-response-envelope--error-model)).

### 8.1 Onboarding — `/api/v1/onboarding`  (`OnboardingController`)

| Method | Path | Auth | Request body | Success | Data (`T`) |
|---|---|---|---|---|---|
| POST | `/signup` | public | `SignupRequest` | 201 · *"Signup successful. Please verify your email to continue."* | `OnboardingSessionResponse` |
| POST | `/{id}/organization-profile` | auth | `UpdateOrganizationRequest` | 200 | `OnboardingSessionResponse` |
| POST | `/{id}/provision-tenant` | auth | — | 200 | `OnboardingSessionResponse` |
| POST | `/{id}/start-trial` | auth | — | 200 | `OnboardingSessionResponse` |
| POST | `/{id}/payment` | auth | `CompletePaymentRequest` *(optional)* | 200 | `OnboardingSessionResponse` |
| POST | `/{id}/activate` | auth | — | 200 | `OnboardingSessionResponse` |
| GET  | `/{id}` | auth | — | 200 | `OnboardingSessionResponse` |

### 8.2 Users — `/api/v1/platform/users`  (`UserController`)

| Method | Path | Auth | Request | Success | Data |
|---|---|---|---|---|---|
| POST | `` | public | `CreateUserRequest` | 201 · *"User created successfully"* | `UserResponse` |
| GET | `/verify-email?token=` | public | query `token` | 200 · *"Email verified successfully. Your account is now active."* | `Void` |
| GET | `` | auth | `Pageable` | 200 | paginated `UserResponse` |
| GET | `/{id}` | auth | — | 200 | `UserResponse` |
| PUT | `/{id}` | auth | `UpdateUserRequest` | 200 | `UserResponse` |
| DELETE | `/{id}` | auth | — | 200 · *"User archived successfully."* | `Void` (soft delete) |

### 8.3 Auth — `/api/v1/platform/auth`  (`AuthController`)

| Method | Path | Auth | Request | Success | Data |
|---|---|---|---|---|---|
| POST | `/login` | public | `LoginRequest` | 200 · *"Login successful"* · sets `omni_session` cookie | `LoginResponse` |
| POST | `/verify-email?token=` | public | query `token` | 200 · *"Email verified successfully. Session established."* · sets cookie | `LoginResponse` |
| GET | `/me` | auth | `@AuthenticationPrincipal` | 200 | `UserResponse` |
| POST | `/logout` | auth | — | 200 · *"Logout successful"* · clears cookie | `Void` |

### 8.4 Organizations — `/api/v1/platform/organizations`  (`OrganizationController`)

| Method | Path | Auth | Request | Success | Data |
|---|---|---|---|---|---|
| POST | `` | auth | `CreateOrganizationRequest` | 201 | `OrganizationResponse` |
| GET | `` | auth | `Pageable` (default page 0, size 20, sort `createdAt` DESC) | 200 | paginated `OrganizationResponse` |
| GET | `/{organizationId}` | auth | — | 200 | `OrganizationResponse` |
| PUT | `/{organizationId}` | auth | `UpdateOrganizationRequest` | 200 | `OrganizationResponse` |
| DELETE | `/{organizationId}` | auth | — | 200 | `Void` (archive / soft delete) |

> On `POST`, the creator is taken from the authenticated principal (`creatorUserId = principal != null ? principal.getUserId() : null`).

### 8.5 Tenant — `/api/v1/tenant`  (`TenantPingController`)

| Method | Path | Auth | Request | Success | Data |
|---|---|---|---|---|---|
| POST | `/ping` | auth | — | 200 · *"Tenant ping succeeded."* | `TenantPingResponse` |

### 8.6 Request / response payloads

**`SignupRequest`** — `firstName`* (≤100), `lastName`* (≤100), `email`* (email, ≤255), `password`* (8–100), `displayName`* (≤225), `organizationType`* (`OrganizationType`). *(\* = required)*

**`CreateUserRequest`** — `firstName`* (≤100), `middleName` (≤100), `lastName`* (≤100), `preferredName` (≤100), `email`* (email ≤255), `phoneCountryCode` (≤5, `^\+?[0-9]{1,4}$`), `phoneNumber` (`^[0-9]{7,20}$`), `profileImageKey` (≤512), `password`* (8–100).

**`UpdateUserRequest`** — all optional: `firstName`, `middleName`, `lastName`, `preferredName`, `phoneCountryCode`, `phoneNumber`, `profileImageKey`.

**`UserResponse`** — `id`, `email`, `firstName`, `middleName`, `lastName`, `preferredName`, `phoneCountryCode`, `phoneNumber`, `profileImageKey`, `status` (`UserStatus`), `emailVerifiedAt`, `lockedUntil`, `lastLoginAt`, `createdAt`, `updatedAt`.

**`CreateOrganizationRequest`** — `organizationCode`* (≤50), `legalName`* (≤225), `displayName`* (≤225), `organizationType`*, `officialEmail`* (email ≤225), `officialPhoneCountryCode` (≤5), `officialPhoneNumber` (`^[0-9]{7,20}$`), `registrationNumber` (≤100), `taxIdentificationNumber` (≤100), `gstNumber` (`^[0-9]{2}[A-Z0-9]{13}$`), `panNumber` (`^[A-Z]{5}[0-9]{4}[A-Z]$`), `websiteUrl` (≤500), `registeredAddressLine1/2` (≤255), `registeredCity/State` (≤100), `registeredPostalCode` (≤20), `registeredCountryCode` (`^[A-Z]{2}$`), `timezone`* (≤100), `currencyCode`* (`^[A-Z]{3}$`), `locale`* (≤20).

**`UpdateOrganizationRequest`** — same fields as create **minus** `organizationCode` and `organizationType`, all optional (patch semantics).

**`OrganizationResponse`** — full profile mirror plus `status` (`OrganizationStatus`), `activatedAt`, `suspendedAt`, `terminatedAt`, `createdAt`, `updatedAt`.

**`LoginRequest`** — `email`* (email), `password`*, `rememberMe` (Boolean, default false). **`LoginResponse`** — `user` (`UserResponse`), `expiresAt`, `tokenType` (`"Bearer"`).

**`CompletePaymentRequest`** — `provider` (`PaymentProvider`), `providerReference` (≤255). Both optional.

**`OnboardingSessionResponse`** — `id`, `organizationId`, `initiatedByUserId`, `status` (`OnboardingStatus`), `currentStep`, `startedAt`, `emailVerifiedAt`, `organizationCompletedAt`, `tenantProvisioningStartedAt`, `trialStartedAt`, `paymentCompletedAt`, `completedAt`, `failedAt`, `failureReason`, `metadata`, `createdAt`, `updatedAt`.

**`TenantPingResponse`** — `tenantId`, `rowCount`, `rows[]` where each `Row` = `id`, `applicationName`, `applicationVersion`, `createdAt`.

---

## 9. Response envelope & error model

Every controller returns `ApiResponse<T>` (`@JsonInclude(NON_NULL)`), built by `ApiResponseBuilder`:

```jsonc
{
  "success": true,
  "code": "OK",                 // or an error code on failure
  "message": "…",
  "data": { /* T */ },
  "errors": [ { "field": "...", "message": "..." } ],  // validation failures
  "pagination": { /* page/size/total… on paginated endpoints */ },
  "meta": { "timestamp": "…", "path": "/api/v1/…" }
}
```

Errors are thrown as `BusinessException` (wrapping a `BaseErrorCode`) and translated by `@RestControllerAdvice GlobalExceptionHandler`:

| Situation | HTTP | Code source |
|---|---|---|
| `BusinessException` | the code's own status | e.g. `OnboardingErrorCode`, `CommonErrorCode`, `TenantErrorCode` |
| `@Valid` body fails (`MethodArgumentNotValidException`) | 400 | `VALIDATION_FAILED` (per-field `errors[]`) |
| Unreadable / malformed body / bad enum | 400 | `BAD_REQUEST` (enum-aware message) |
| Wrong HTTP method | 405 | — |
| Anything uncaught | 500 | `INTERNAL_SERVER_ERROR` |

`CommonErrorCode`: `BAD_REQUEST`/`VALIDATION_FAILED` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `RESOURCE_NOT_FOUND` (404), `RESOURCE_ALREADY_EXISTS` (409), `INTERNAL_SERVER_ERROR` (500).

`OnboardingErrorCode`: `ONBOARDING_SESSION_NOT_FOUND` (404), `ONBOARDING_ALREADY_EXISTS` (409), `INVALID_ONBOARDING_STATE` (409), `DEFAULT_TRIAL_PLAN_NOT_FOUND` (500), `ORGANIZATION_CODE_GENERATION_FAILED` (500).

---

## 10. Data model (database schema)

### Platform database — `db/platform/migration`

| Migration | What it adds |
|---|---|
| **V1** `platform_core.sql` | `pgcrypto` + `citext` extensions; all platform enums; core tables: `platform_users`, `platform_tokens`, `platform_sessions`, `platform_roles`/`permissions`/`role_permissions`/`user_roles`, `platform_organizations`, `platform_organization_memberships`, `platform_organization_onboarding`, `platform_plans`, `platform_subscriptions` (+ indexes, CHECKs, unique constraints). |
| **V2** `schema_fixes.sql` | fixes the "one primary member **per organization**" unique index; adds `initiated_by_user_id` to onboarding; adds `created_by`/`cancelled_by` to subscriptions; adds `platform_audit_log`, `platform_features` + `platform_plan_features`, and **`tenant_databases`** (with `uq_tenant_databases_active_org` partial unique index). |
| **V3** `onboarding_provisioning_jobs.sql` | makes onboarding uniqueness a partial index (allows re-onboarding after soft-delete); adds **`platform_provisioning_jobs`**. |
| **V4** `seed_default_trial_plan.sql` | idempotently seeds the `TRIAL` plan (`trial_days = 14`, price 0, `INR`) that `start-trial` depends on. |
| **V5** `align_lifecycle_timestamp_checks.sql` | re-anchors lifecycle CHECKs on `tenant_databases` and `platform_provisioning_jobs` to **business** columns (e.g. `ready_at >= provisioned_at`) instead of the audit `created_at`, which is unsatisfiable for ORM-inserted rows. |
| **V6** `fix_platform_sessions_last_used_check.sql` | drops the unsatisfiable `last_used_at >= created_at` CHECK that was breaking every login (same audit-clock trap as V5). |

Notable columns/types: emails are `CITEXT`; enums are Postgres `NAMED_ENUM` types mapped with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`; `jsonb` metadata columns via `SqlTypes.JSON`; `platform_sessions.ip_address` is `INET` (mapped with `SqlTypes.INET`). `tenant_databases` intentionally has **no credential columns**.

### Tenant database — `db/tenant/migration`

| Migration | What it adds |
|---|---|
| **V1** `initial_schema.sql` | `application_metadata` (`BIGSERIAL id`, `application_name`, `application_version`, `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`) + one seed row `('OmniHealth','0.0.1')`. Applied **programmatically** to each new tenant DB during provisioning, not by Boot's Flyway. |

---

## 11. Configuration & profiles

- `application.yaml` — base: app name, active profile `dev`, Docker Compose disabled, server port `8080`.
- `application-dev.yaml` / `application-test.yaml` / `application-prod.yaml` — each defines `spring.datasource` (platform DB), `ddl-auto: validate`, Flyway on `db/platform/migration`, mail, and the **`app.tenant.*`** block. Prod uses `${DB_URL}`/`${DB_USERNAME}`/`${DB_PASSWORD}` + `${DB_HOST}`/`${DB_PORT}`; the mail password is `${RESEND_API_KEY}`.
- **`app.tenant.*`** (bound by `TenantProvisioningProperties`, `@Validated`):
  - `admin` — `{ url, username, password }` → the `tenantAdminDataSource` used for `CREATE DATABASE` and as the multi-tenancy bootstrap connection.
  - `connection` — `{ host, port(=5432), username, password, schema(=public), maxPoolSize(=5) }` → credentials for the per-tenant Hikari pools.
  - `flyway` — `{ locations(=classpath:db/tenant/migration), baselineOnMigrate(=true) }`.
- **App entry point** `OmnihealthBackendApplication`: `@SpringBootApplication @EnableAsync`, loads a `.env` file if present, and forces the JVM default timezone to **UTC**. `JpaAuditConfig` enables `@CreatedDate`/`@LastModifiedDate` auditing.

> Secrets (DB passwords, `RESEND_API_KEY`) live in the profile YAML / environment — they are **not** reproduced in this document and must not be committed.

---

## 12. The series of files — bottom to top

Read in this order. Each layer depends only on the layers **above** it in the list, so by the time you reach the controllers you'll understand everything they rely on. Paths are under `src/main/`.

### Layer 0 — Database schema (the foundation)
1. `resources/db/platform/migration/V1__platform_core.sql`
2. `resources/db/platform/migration/V2__schema_fixes.sql`
3. `resources/db/platform/migration/V3__onboarding_provisioning_jobs.sql`
4. `resources/db/platform/migration/V4__seed_default_trial_plan.sql`
5. `resources/db/platform/migration/V5__align_lifecycle_timestamp_checks.sql`
6. `resources/db/platform/migration/V6__fix_platform_sessions_last_used_check.sql`
7. `resources/db/tenant/migration/V1__initial_schema.sql`

### Layer 1 — Bootstrap & configuration
8. `resources/application.yaml` → `application-dev.yaml` / `application-test.yaml` / `application-prod.yaml`
9. `java/com/omnihealth/OmnihealthBackendApplication.java`
10. `config/audit/JpaAuditConfig.java`
11. `config/JacksonConfig.java`, `config/CorsConfig.java`, `config/OpenApiConfig.java`
12. `config/persistence/TenantProvisioningProperties.java`
13. `config/persistence/TenantAdminDataSourceConfig.java`
14. `config/persistence/PlatformPersistenceConfig.java`
15. `config/persistence/TenantPersistenceConfig.java`

### Layer 2 — Common infrastructure
16. `common/constants/ApiRoutes.java`
17. `common/entity/BaseEntity.java`
18. `common/response/{ApiResponse, ApiMeta, ApiError, PaginationMeta, PageResponse}.java`
19. `common/util/PageMapper.java`, `common/builder/ApiResponseBuilder.java`
20. `common/exception/{BaseErrorCode, CommonErrorCode, BusinessException, …, GlobalExceptionHandler}.java`
21. `common/validation/**` (annotations + validators)
22. `common/security/PlatformUserPrincipal.java`

### Layer 3 — Enums
23. `common/enums/{UserStatus, TokenType, AuthProvider}.java`
24. `platform/organization/entity/{OrganizationType, OrganizationStatus}.java`
25. `platform/billing/enums/{BillingCycle, SubscriptionStatus, PaymentProvider}.java`
26. `platform/tenant/enums/{TenantDatabaseStatus, ProvisioningJobStatus}.java`
27. `platform/onboarding/enums/{OnboardingStatus, OnboardingStep}.java`

### Layer 4 — Entities
28. `platform/user/entity/{User, VerificationToken}.java`
29. `platform/auth/entity/PlatformSession.java`
30. `platform/organization/entity/{Organization, PlatformOrganizationMembership}.java`
31. `platform/billing/entity/{Plan, Subscription}.java`
32. `platform/tenant/entity/{TenantDatabase, ProvisioningJob}.java`
33. `platform/onboarding/entity/OnboardingSession.java`
34. `tenant/metadata/entity/ApplicationMetadata.java` *(tenant EMF)*

### Layer 5 — Repositories
35. `platform/user/repository/{UserRepository, VerificationTokenRepository}.java`
36. `platform/auth/repository/PlatformSessionRepository.java`
37. `platform/organization/repository/{OrganizationRepository, PlatformOrganizationMembershipRepository}.java` *(note `findPrimaryOrganizationIdByUserId`)*
38. `platform/billing/repository/{PlanRepository, SubscriptionRepository}.java`
39. `platform/tenant/repository/{TenantDatabaseRepository, ProvisioningJobRepository}.java`
40. `platform/onboarding/repository/OnboardingSessionRepository.java`
41. `tenant/metadata/repository/ApplicationMetadataRepository.java` *(tenant EMF)*

### Layer 6 — DTOs & mappers
42. `platform/user/dto/**`, `platform/user/mapper/UserMapper.java`
43. `platform/organization/dto/**`, `platform/organization/mapper/OrganizationMapper.java`
44. `platform/auth/dto/**`
45. `platform/onboarding/dto/**`, `platform/onboarding/mapper/OnboardingMapper.java`
46. `tenant/metadata/dto/TenantPingResponse.java`

### Layer 7 — Domain services
47. `platform/user/service/EmailService.java` + `event/EmailVerifiedEvent.java`
48. `platform/user/service/UserService.java` + `impl/UserServiceImpl.java`
49. `platform/auth/service/{SessionService, AuthService}.java` + impls
50. `platform/organization/service/OrganizationService.java` + `impl/OrganizationServiceImpl.java`

### Layer 8 — Provisioning & routing (the multi-tenant core)
51. `platform/tenant/exception/{TenantErrorCode, TenantNotProvisionedException, TenantProvisioningException}.java`
52. `platform/tenant/provisioning/TenantDatabaseAdmin.java`
53. `platform/tenant/provisioning/TenantFlywayMigrator.java`
54. `platform/tenant/provisioning/TenantProvisioningTxService.java`
55. `platform/tenant/provisioning/TenantProvisioningService.java`
56. `tenant/context/{TenantContext, TenantIdentifierResolver, TenantContextFilter}.java`
57. `tenant/connection/TenantConnectionProvider.java`
58. `tenant/metadata/service/TenantPingService.java`

### Layer 9 — Onboarding orchestration
59. `platform/onboarding/exception/{OnboardingErrorCode, OnboardingException, OnboardingNotFoundException}.java`
60. `platform/onboarding/service/OnboardingService.java` + `impl/OnboardingServiceImpl.java`  ← **the heart of the flow**
61. `platform/onboarding/listener/OnboardingEventListener.java`

### Layer 10 — Security wiring (filters)
62. `common/security/SessionAuthenticationFilter.java`
63. `common/security/SecurityConfig.java`

### Layer 11 — Controllers (the top / HTTP surface)
64. `platform/user/controller/UserController.java`
65. `platform/auth/controller/AuthController.java`
66. `platform/organization/controller/OrganizationController.java`
67. `platform/onboarding/controller/OnboardingController.java`
68. `tenant/metadata/controller/TenantPingController.java`

---

### Suggested first read-through

If you only have time for the critical path, read these seven in order:

1. `V1__platform_core.sql` (the schema you're modeling)
2. `OnboardingController.java` (the HTTP entry points)
3. `OnboardingServiceImpl.java` (the state machine)
4. `UserServiceImpl.java` + `OnboardingEventListener.java` (how email verification advances onboarding)
5. `TenantProvisioningService.java` (the async worker pipeline)
6. `TenantConnectionProvider.java` + `TenantContextFilter.java` (runtime routing)
7. `SecurityConfig.java` (how the filters are ordered and what's public)

That path takes you from the database, through the API, through the state machine, into the background provisioning, and finally into the per-request routing — the whole registration → tenant-provisioning story end to end.
