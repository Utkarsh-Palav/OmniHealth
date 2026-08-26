-- =====================================================
-- OmniHealth Platform Database
-- Version : V2
-- PostgreSQL : 17
-- Flyway Migration
-- =====================================================

-- =====================================================
-- SECTION 1 : EXTENSIONS
-- =====================================================

create EXTENSION IF NOT EXISTS pgcrypto;
create EXTENSION IF NOT EXISTS citext;

-- =====================================================
-- SECTION 2 : PLATFORM ENUMS
-- =====================================================

-- -------------------------
-- Platform User Status
-- -------------------------

create type platform_user_status as ENUM (
    'PENDING_EMAIL_VERIFICATION',
    'ACTIVE',
    'LOCKED',
    'SUSPENDED',
    'DELETED'
);

-- -------------------------
-- Platform Role Code
-- -------------------------

create type platform_role_code as ENUM (
    'PLATFORM_SUPER_ADMIN',
    'PLATFORM_SUPPORT',
    'PLATFORM_FINANCE',
    'PLATFORM_OPERATIONS'
);

-- -------------------------
-- Organization Status
-- -------------------------

create type platform_organization_status as ENUM (
    'DRAFT',
    'ACTIVE',
    'SUSPENDED',
    'TERMINATED'
);

-- -------------------------
-- Organization Type
-- -------------------------

create type organization_type as ENUM (
    'CLINIC',
    'HOSPITAL',
    'DIAGNOSTIC_CENTER',
    'LABORATORY',
    'PHARMACY',
    'OTHER'
);

-- -------------------------
-- Billing Cycle
-- -------------------------

create type platform_billing_cycle as ENUM (
    'MONTHLY',
    'QUARTERLY',
    'YEARLY'
);

-- -------------------------
-- Subscription Status
-- -------------------------

create type platform_subscription_status as ENUM (
    'TRIAL',
    'ACTIVE',
    'PAST_DUE',
    'SUSPENDED',
    'CANCELLED',
    'EXPIRED'
);

-- -------------------------
-- Payment Status
-- -------------------------

create type platform_payment_status as ENUM (
    'PENDING',
    'AUTHORIZED',
    'CAPTURED',
    'FAILED',
    'REFUNDED',
    'PARTIALLY_REFUNDED'
);

-- -------------------------
-- Payment Provider
-- -------------------------

create type platform_payment_provider as ENUM (
    'RAZORPAY',
    'STRIPE',
    'MANUAL'
);

-- -------------------------
-- Onboarding Status
-- -------------------------

create type organization_onboarding_status as ENUM (
    'ACCOUNT_CREATED',
    'EMAIL_VERIFIED',
    'ORGANIZATION_CREATED',
    'TENANT_PROVISIONING',
    'TRIAL_STARTED',
    'PAYMENT_COMPLETED',
    'ACTIVE',
    'FAILED'
);

-- -------------------------
-- Tenant Database Status
-- -------------------------

create type tenant_database_status as ENUM (
    'PENDING',
    'PROVISIONING',
    'READY',
    'FAILED',
    'ARCHIVED'
);

-- -------------------------
-- Payment Transaction Status
-- -------------------------

create type payment_transaction_status as ENUM (
    'INITIATED',
    'PROCESSING',
    'SUCCESS',
    'FAILED',
    'CANCELLED'
);

-- -------------------------
-- Invoice Status
-- -------------------------

create type platform_invoice_status as ENUM (
    'DRAFT',
    'ISSUED',
    'PARTIALLY_PAID',
    'PAID',
    'VOID',
    'OVERDUE'
);

-- -------------------------
-- Feature Value Type
-- -------------------------

create type platform_feature_value_type as ENUM (
    'BOOLEAN',
    'INTEGER',
    'DECIMAL',
    'STRING'
);

-- -------------------------
-- Provisioning Job Status
-- -------------------------

create type platform_provisioning_job_status as ENUM (
    'QUEUED',
    'RUNNING',
    'COMPLETED',
    'FAILED'
);

-- -------------------------
-- Audit Action Type
-- -------------------------

create type platform_audit_action_type as ENUM (
    'CREATE',
    'UPDATE',
    'DELETE',
    'LOGIN',
    'LOGOUT',
    'ACCESS',
    'EXPORT',
    'IMPORT'
);

-- -------------------------
-- Token Type
-- -------------------------

create type platform_token_type as ENUM (
    'EMAIL_VERIFICATION',
    'PASSWORD_RESET',
    'REFRESH_TOKEN',
    'MAGIC_LINK',
    'MFA_VERIFICATION',
    'INVITATION',
    'API_KEY'
);

-- =====================================================
-- SECTION 3 : PLATFORM IDENTITY
-- =====================================================

-- -----------------------------------------------------
-- Table : platform_users
-- Purpose : Stores the global identity of every user
--           registered on the OmniHealth platform.
--           A platform user may belong to one or more
--           organizations through tenant_memberships.
-- -----------------------------------------------------

CREATE TABLE platform_users (

    id UUID PRIMARY KEY DEFAULT gen_random_UUID(),

    email CITEXT NOT NULL,
    password_hash TEXT NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    preferred_name VARCHAR(100),

    phone_country_code VARCHAR(5),

    phone_number VARCHAR(20),

    profile_image_key VARCHAR(512),

    status platform_user_status NOT NULL DEFAULT 'PENDING_EMAIL_VERIFICATION',

    email_verified_at TIMESTAMPTZ,

    failed_login_attempts SMALLINT NOT NULL DEFAULT 0,

    locked_until TIMESTAMPTZ,

    last_login_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_users_email UNIQUE (email),

    CONSTRAINT chk_platform_users_failed_login_attempts
        CHECK (failed_login_attempts >= 0)
);

COMMENT ON COLUMN platform_users.profile_image_key IS
    'Storage object key for the user profile image. The application resolves this key to a signed or public URL using the configured object storage provider.';

-- -----------------------------------------------------
-- Indexes : platform_users
-- -----------------------------------------------------

CREATE INDEX idx_platform_users_status
ON platform_users(status);

CREATE INDEX idx_platform_users_last_login_at
ON platform_users(last_login_at);

CREATE INDEX idx_platform_users_deleted_at
ON platform_users(deleted_at);

CREATE INDEX idx_platform_users_created_at
ON platform_users(created_at);

-- =====================================================
-- PLATFORM IDENTITY
-- Table : platform_tokens
-- =====================================================

CREATE TABLE platform_tokens (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    platform_user_id UUID NOT NULL,

    token_type platform_token_type NOT NULL,

    token_hash VARCHAR(128) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign Key
    CONSTRAINT fk_platform_tokens_user
        FOREIGN KEY (platform_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT,

    -- Token hash must always exist
    CONSTRAINT uq_platform_tokens_hash
        UNIQUE (token_hash),

    -- Lifecycle consistency
    CONSTRAINT chk_platform_tokens_consumed_after_creation
        CHECK (
            consumed_at IS NULL
            OR consumed_at >= created_at
        ),

    CONSTRAINT chk_platform_tokens_revoked_after_creation
        CHECK (
            revoked_at IS NULL
            OR revoked_at >= created_at
        )
);

COMMENT ON COLUMN platform_tokens.token_hash IS
'Cryptographic hash of the raw token. Raw authentication tokens must never be persisted.';

COMMENT ON COLUMN platform_tokens.metadata IS
'Optional JSON metadata associated with the token lifecycle or authentication flow.';

-- =====================================================
-- Indexes : platform_tokens
-- =====================================================

CREATE INDEX idx_platform_tokens_user
ON platform_tokens(platform_user_id);

CREATE INDEX idx_platform_tokens_type
ON platform_tokens(token_type);

CREATE INDEX idx_platform_tokens_expires_at
ON platform_tokens(expires_at);

CREATE INDEX idx_platform_tokens_user_type
ON platform_tokens(platform_user_id, token_type);

CREATE INDEX idx_platform_tokens_active
ON platform_tokens(platform_user_id, token_type, expires_at)
WHERE consumed_at IS NULL
  AND revoked_at IS NULL
  AND deleted_at IS NULL;

-- =====================================================
-- PLATFORM IDENTITY
-- Table : platform_sessions
-- =====================================================

CREATE TABLE platform_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    platform_user_id UUID NOT NULL,

    refresh_token_hash VARCHAR(128) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,

    revoked_at TIMESTAMPTZ,

    ip_address INET,
    user_agent TEXT,

    device_id VARCHAR(225),

    metadata JSONB,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign Key
    CONSTRAINT fk_platform_sessions_user
        FOREIGN KEY (platform_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT,

    -- One refresh-token hash can belong to only one session
    CONSTRAINT uq_platform_sessions_refresh_token
        UNIQUE (refresh_token_hash),

    -- Expiration must be after creation
    CONSTRAINT chk_platform_sessions_expiration
        CHECK (expires_at > created_at),

    -- Last-used timestamp cannot precede creation
    CONSTRAINT chk_platform_sessions_last_used
        CHECK (
            last_used_at IS NULL
            OR last_used_at >= created_at
        ),

    -- Revocation cannot precede creation
    CONSTRAINT chk_platform_sessions_revoked
        CHECK (
            revoked_at IS NULL
            OR revoked_at >= created_at
        )
);

COMMENT ON COLUMN platform_sessions.refresh_token_hash IS
'Cryptographic hash of the refresh token. The raw refresh token must never be persisted.';

COMMENT ON COLUMN platform_sessions.ip_address IS
'IP address associated with the session for security auditing and anomaly detection.';

COMMENT ON COLUMN platform_sessions.user_agent IS
'Client user-agent captured when the session is established.';

COMMENT ON COLUMN platform_sessions.device_id IS
'Application-generated device identifier used to distinguish concurrent sessions.';

COMMENT ON COLUMN platform_sessions.metadata IS
'Optional JSON metadata associated with the authenticated session.';

-- =====================================================
-- Indexes : platform_sessions
-- =====================================================

CREATE INDEX idx_platform_sessions_user
ON platform_sessions(platform_user_id);

CREATE INDEX idx_platform_sessions_expires_at
ON platform_sessions(expires_at);

CREATE INDEX idx_platform_sessions_last_used_at
ON platform_sessions(last_used_at);

CREATE INDEX idx_platform_sessions_device
ON platform_sessions(device_id);

CREATE INDEX idx_platform_sessions_active
ON platform_sessions(platform_user_id, expires_at)
WHERE revoked_at IS NULL
  AND deleted_at IS NULL;

-- =====================================================
-- PLATFORM ACCESS CONTROL
-- Table : platform_roles
-- =====================================================

CREATE TABLE platform_roles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    code platform_role_code NOT NULL,

    name VARCHAR(100) NOT NULL,

    description VARCHAR(500),

    is_system_role BOOLEAN NOT NULL DEFAULT TRUE,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_roles_code UNIQUE (code)
);

COMMENT ON TABLE platform_roles IS
'Defines roles used by OmniHealth platform-level users. These roles control access to platform administration capabilities and are separate from tenant-level roles.';

COMMENT ON COLUMN platform_roles.code IS
'Stable system identifier for the platform role.';

COMMENT ON COLUMN platform_roles.is_system_role IS
'Indicates whether the role is a built-in platform role that cannot be freely deleted or replaced by customers.';

COMMENT ON COLUMN platform_roles.is_active IS
'Controls whether the platform role can currently be assigned or used.';

-- =====================================================
-- Indexes : platform_roles
-- =====================================================

CREATE INDEX idx_platform_roles_active
ON platform_roles(is_active);

CREATE INDEX idx_platform_roles_deleted_at
ON platform_roles(deleted_at);

-- =====================================================
-- PLATFORM ACCESS CONTROL
-- Table : platform_permissions
-- =====================================================

CREATE TABLE platform_permissions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    code VARCHAR(150) NOT NULL,

    name VARCHAR(150) NOT NULL,

    description VARCHAR(500),

    resource VARCHAR(100) NOT NULL,

    action VARCHAR(50) NOT NULL,

    is_system_permission BOOLEAN NOT NULL DEFAULT TRUE,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_permissions_code
        UNIQUE (code),

    CONSTRAINT uq_platform_permissions_resource_action
        UNIQUE (resource, action)

);

COMMENT ON TABLE platform_permissions IS
'Defines atomic permissions for platform-level access control. Permissions are assigned to platform roles and are independent of tenant-level permissions.';

COMMENT ON COLUMN platform_permissions.code IS
'Stable machine-readable permission identifier, for example ORGANIZATION_READ or BILLING_MANAGE.';

COMMENT ON COLUMN platform_permissions.resource IS
'Platform resource protected by the permission, for example ORGANIZATION, USER, BILLING, or TENANT.';

COMMENT ON COLUMN platform_permissions.action IS
'Operation allowed on the resource, for example READ, CREATE, UPDATE, DELETE, or MANAGE.';

COMMENT ON COLUMN platform_permissions.is_system_permission IS
'Indicates whether the permission is a built-in platform permission managed by the system.';

-- =====================================================
-- Indexes : platform_permissions
-- =====================================================

CREATE INDEX idx_platform_permissions_resource
ON platform_permissions(resource);

CREATE INDEX idx_platform_permissions_action
ON platform_permissions(action);

CREATE INDEX idx_platform_permissions_active
ON platform_permissions(is_active);

CREATE INDEX idx_platform_permissions_deleted_at
ON platform_permissions(deleted_at);

-- =====================================================
-- PLATFORM ACCESS CONTROL
-- Table : platform_role_permissions
-- =====================================================

CREATE TABLE platform_role_permissions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    platform_role_id UUID NOT NULL,

    platform_permission_id UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_platform_role_permissions_role
        FOREIGN KEY (platform_role_id)
        REFERENCES platform_roles(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_role_permissions_permission
        FOREIGN KEY (platform_permission_id)
        REFERENCES platform_permissions(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_platform_role_permissions
        UNIQUE (
            platform_role_id,
            platform_permission_id
        )

);

COMMENT ON TABLE platform_role_permissions IS
'Associates platform-level roles with their allowed permissions.';

COMMENT ON COLUMN platform_role_permissions.platform_role_id IS
'Reference to the platform role receiving the permission.';

COMMENT ON COLUMN platform_role_permissions.platform_permission_id IS
'Reference to the platform permission granted to the role.';

-- =====================================================
-- Indexes : platform_role_permissions
-- =====================================================

CREATE INDEX idx_platform_role_permissions_role
ON platform_role_permissions(platform_role_id);

CREATE INDEX idx_platform_role_permissions_permission
ON platform_role_permissions(platform_permission_id);

CREATE INDEX idx_platform_role_permissions_active
ON platform_role_permissions(platform_role_id, platform_permission_id)
WHERE deleted_at IS NULL;

-- =====================================================
-- PLATFORM ACCESS CONTROL
-- Table : platform_user_roles
-- =====================================================

CREATE TABLE platform_user_roles (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    platform_user_id UUID NOT NULL,

    platform_role_id UUID NOT NULL,

    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_platform_user_roles_user
        FOREIGN KEY (platform_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_user_roles_role
        FOREIGN KEY (platform_role_id)
        REFERENCES platform_roles(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_platform_user_roles
        UNIQUE (
            platform_user_id,
            platform_role_id
        ),

    CONSTRAINT chk_platform_user_roles_assigned_at
        CHECK (assigned_at >= created_at)
);

COMMENT ON TABLE platform_user_roles IS
'Associates platform users with platform-level roles. This access-control relationship is separate from tenant-level user roles.';

COMMENT ON COLUMN platform_user_roles.platform_user_id IS
'Platform user receiving the platform role.';

COMMENT ON COLUMN platform_user_roles.platform_role_id IS
'Platform-level role assigned to the user.';

COMMENT ON COLUMN platform_user_roles.assigned_at IS
'Timestamp when the platform role was assigned to the user.';

-- =====================================================
-- Indexes : platform_user_roles
-- =====================================================

CREATE INDEX idx_platform_user_roles_user
ON platform_user_roles(platform_user_id);

CREATE INDEX idx_platform_user_roles_role
ON platform_user_roles(platform_role_id);

CREATE INDEX idx_platform_user_roles_active
ON platform_user_roles(platform_user_id, platform_role_id)
WHERE deleted_at IS NULL;

-- =====================================================
-- ORGANIZATION MANAGEMENT
-- Table : platform_organizations
-- =====================================================

CREATE TABLE platform_organizations (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_code VARCHAR(50) NOT NULL,

    legal_name VARCHAR(225) NOT NULL,
    display_name VARCHAR(225) NOT NULL,

    organization_type organization_type NOT NULL,

    status platform_organization_status NOT NULL DEFAULT 'DRAFT',

    registration_number VARCHAR(100),

    tax_identification_number VARCHAR(100),

    gst_number VARCHAR(15),

    pan_number VARCHAR(10),

    official_email CITEXT NOT NULL,

    official_phone_country_code VARCHAR(5),

    official_phone_number VARCHAR(20),

    website_url VARCHAR(500),

    registered_address_line1 VARCHAR(255),

    registered_address_line2 VARCHAR(255),

    registered_city VARCHAR(100),

    registered_state VARCHAR(100),

    registered_postal_code VARCHAR(20),

    registered_country_code VARCHAR(2),

    timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Kolkata',

    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',

    locale VARCHAR(20) NOT NULL DEFAULT 'en-IN',

    activated_at TIMESTAMPTZ,

    suspended_at TIMESTAMPTZ,

    terminated_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_organizations_code
        UNIQUE (organization_code),

    CONSTRAINT uq_platform_organizations_official_email
        UNIQUE (official_email),

    CONSTRAINT chk_platform_organizations_gst
        CHECK (
            gst_number IS NULL
            OR gst_number ~ '^[0-9]{2}[A-Z0-9]{13}$'
        ),

    CONSTRAINT chk_platform_organizations_pan
        CHECK (
            pan_number IS NULL
            OR pan_number ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'
        ),

    CONSTRAINT chk_platform_organizations_postal_code
        CHECK (
            registered_postal_code IS NULL
            OR LENGTH(TRIM(registered_postal_code)) >= 3
        ),

    CONSTRAINT chk_platform_organizations_activation
        CHECK (
            activated_at IS NULL
            OR activated_at >= created_at
        ),

    CONSTRAINT chk_platform_organizations_suspension
        CHECK (
            suspended_at IS NULL
            OR suspended_at >= created_at
        ),

    CONSTRAINT chk_platform_organizations_termination
        CHECK (
            terminated_at IS NULL
            OR terminated_at >= created_at
        )

);

COMMENT ON TABLE platform_organizations IS
'Master record for healthcare organizations onboarded onto the OmniHealth platform. Organization records live in the platform database while operational healthcare data is isolated in the organization tenant database.';

COMMENT ON COLUMN platform_organizations.organization_code IS
'Stable unique platform identifier for the organization.';

COMMENT ON COLUMN platform_organizations.legal_name IS
'Registered legal name of the organization.';

COMMENT ON COLUMN platform_organizations.display_name IS
'Customer-facing organization name.';

COMMENT ON COLUMN platform_organizations.registration_number IS
'Government or legally assigned organization registration number where applicable.';

COMMENT ON COLUMN platform_organizations.tax_identification_number IS
'Organization tax identification number where applicable.';

COMMENT ON COLUMN platform_organizations.gst_number IS
'Indian Goods and Services Tax identification number, when applicable.';

COMMENT ON COLUMN platform_organizations.pan_number IS
'Indian Permanent Account Number associated with the organization, when applicable.';

COMMENT ON COLUMN platform_organizations.timezone IS
'IANA timezone used for organization-level scheduling and date/time presentation.';

COMMENT ON COLUMN platform_organizations.currency_code IS
'ISO 4217 currency code used for organization billing and financial presentation.';

-- =====================================================
-- Indexes : platform_organizations
-- =====================================================

CREATE INDEX idx_platform_organizations_status
ON platform_organizations(status);

CREATE INDEX idx_platform_organizations_type
ON platform_organizations(organization_type);

CREATE INDEX idx_platform_organizations_created_at
ON platform_organizations(created_at);

CREATE INDEX idx_platform_organizations_deleted_at
ON platform_organizations(deleted_at);

CREATE INDEX idx_platform_organizations_status_active
ON platform_organizations(status)
WHERE deleted_at IS NULL;

-- =====================================================
-- ORGANIZATION MANAGEMENT
-- Table : platform_organization_memberships
-- =====================================================

CREATE TABLE platform_organization_memberships (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    platform_user_id UUID NOT NULL,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    left_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_platform_org_memberships_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_org_memberships_user
        FOREIGN KEY (platform_user_id)
        REFERENCES platform_users(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_platform_org_memberships
        UNIQUE (
            organization_id,
            platform_user_id
        ),

    CONSTRAINT chk_platform_org_membership_left_at
        CHECK (
            left_at IS NULL
            OR left_at >= joined_at
        )

);

COMMENT ON TABLE platform_organization_memberships IS
'Associates global platform identities with customer organizations. Tenant-level roles and permissions are managed separately inside the tenant database.';

COMMENT ON COLUMN platform_organization_memberships.is_primary IS
'Indicates the user''s primary organization on the platform.';

COMMENT ON COLUMN platform_organization_memberships.joined_at IS
'Timestamp when the user joined the organization.';

COMMENT ON COLUMN platform_organization_memberships.left_at IS
'Timestamp when the user left the organization. NULL indicates that the membership has not ended.';

-- =====================================================
-- Indexes : platform_organization_memberships
-- =====================================================

CREATE INDEX idx_platform_org_memberships_user
ON platform_organization_memberships(platform_user_id);

CREATE INDEX idx_platform_org_memberships_organization
ON platform_organization_memberships(organization_id);

CREATE INDEX idx_platform_org_memberships_active
ON platform_organization_memberships(
    platform_user_id,
    organization_id
)
WHERE deleted_at IS NULL
  AND left_at IS NULL;

-- -----------------------------------------------------
-- Only one primary organization per user
-- -----------------------------------------------------

CREATE UNIQUE INDEX uq_platform_org_memberships_primary
ON platform_organization_memberships(platform_user_id)
WHERE is_primary = TRUE
  AND deleted_at IS NULL
  AND left_at IS NULL;

-- =====================================================
-- ORGANIZATION ONBOARDING
-- Table : platform_organization_onboarding
-- =====================================================

CREATE TABLE platform_organization_onboarding (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    status organization_onboarding_status NOT NULL
        DEFAULT 'ACCOUNT_CREATED',

    current_step VARCHAR(100) NOT NULL,

    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    email_verified_at TIMESTAMPTZ,

    organization_completed_at TIMESTAMPTZ,

    tenant_provisioning_started_at TIMESTAMPTZ,

    trial_started_at TIMESTAMPTZ,

    payment_completed_at TIMESTAMPTZ,

    completed_at TIMESTAMPTZ,

    failed_at TIMESTAMPTZ,

    failure_reason TEXT,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_platform_org_onboarding_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_platform_org_onboarding_organization
        UNIQUE (organization_id),

    CONSTRAINT chk_platform_org_onboarding_completed
        CHECK (
            completed_at IS NULL
            OR completed_at >= started_at
        ),

    CONSTRAINT chk_platform_org_onboarding_failed
        CHECK (
            failed_at IS NULL
            OR failed_at >= started_at
        )
);

COMMENT ON TABLE platform_organization_onboarding IS
'Tracks the lifecycle and progress of an organization through the OmniHealth customer onboarding workflow.';

COMMENT ON COLUMN platform_organization_onboarding.status IS
'Current onboarding lifecycle state of the organization.';

COMMENT ON COLUMN platform_organization_onboarding.current_step IS
'Application-level onboarding step currently requiring completion.';

COMMENT ON COLUMN platform_organization_onboarding.failure_reason IS
'Reason recorded when onboarding enters a failed state.';

COMMENT ON COLUMN platform_organization_onboarding.metadata IS
'Optional JSON metadata associated with onboarding progress or external workflow state.';

-- =====================================================
-- Indexes : platform_organization_onboarding
-- =====================================================

CREATE INDEX idx_platform_org_onboarding_status
ON platform_organization_onboarding(status);

CREATE INDEX idx_platform_org_onboarding_current_step
ON platform_organization_onboarding(current_step);

CREATE INDEX idx_platform_org_onboarding_started_at
ON platform_organization_onboarding(started_at);

CREATE INDEX idx_platform_org_onboarding_active
ON platform_organization_onboarding(organization_id, status)
WHERE deleted_at IS NULL;

-- =====================================================
-- BILLING & SUBSCRIPTIONS
-- Table : platform_plans
-- =====================================================

CREATE TABLE platform_plans (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    code VARCHAR(50) NOT NULL,

    name VARCHAR(100) NOT NULL,

    description VARCHAR(500),

    billing_cycle platform_billing_cycle NOT NULL,

    price NUMERIC(12,2) NOT NULL,

    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',

    trial_days INTEGER NOT NULL DEFAULT 0,

    is_public BOOLEAN NOT NULL DEFAULT TRUE,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    sort_order INTEGER NOT NULL DEFAULT 0,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_platform_plans_code
        UNIQUE (code),

    CONSTRAINT chk_platform_plans_price
        CHECK (price >= 0),

    CONSTRAINT chk_platform_plans_trial_days
        CHECK (trial_days >= 0),

    CONSTRAINT chk_platform_plans_sort_order
        CHECK (sort_order >= 0)
);

COMMENT ON TABLE platform_plans IS
'Defines the subscription plans offered by the OmniHealth platform. Plans are reusable product definitions and are separate from organization subscriptions.';

COMMENT ON COLUMN platform_plans.code IS
'Stable machine-readable identifier for the plan.';

COMMENT ON COLUMN platform_plans.billing_cycle IS
'Billing frequency for the plan.';

COMMENT ON COLUMN platform_plans.price IS
'Base recurring price for the selected billing cycle.';

COMMENT ON COLUMN platform_plans.currency_code IS
'ISO 4217 currency code used for the plan price.';

COMMENT ON COLUMN platform_plans.trial_days IS
'Number of trial days granted when an organization starts this plan.';

COMMENT ON COLUMN platform_plans.is_public IS
'Controls whether the plan is visible to customers during self-service onboarding.';

COMMENT ON COLUMN platform_plans.is_active IS
'Controls whether the plan can currently be selected for new subscriptions.';

COMMENT ON COLUMN platform_plans.metadata IS
'Optional JSON metadata for plan configuration and future billing integrations.';

-- =====================================================
-- Indexes : platform_plans
-- =====================================================

CREATE INDEX idx_platform_plans_active
ON platform_plans(is_active);

CREATE INDEX idx_platform_plans_public
ON platform_plans(is_public);

CREATE INDEX idx_platform_plans_billing_cycle
ON platform_plans(billing_cycle);

CREATE INDEX idx_platform_plans_sort_order
ON platform_plans(sort_order);

CREATE INDEX idx_platform_plans_active_public
ON platform_plans(sort_order)
WHERE is_active = TRUE
  AND is_public = TRUE
  AND deleted_at IS NULL;

-- =====================================================
-- PLATFORM BILLING
-- Table : platform_subscriptions
-- Purpose:
--     Represents an organization's subscription to a
--     platform plan.
--
--     The subscription lifecycle is independent from
--     individual payment transactions.
-- =====================================================

CREATE TABLE platform_subscriptions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    plan_id UUID NOT NULL,

    status platform_subscription_status NOT NULL DEFAULT 'TRIAL',

    billing_cycle platform_billing_cycle NOT NULL,

    price NUMERIC(19,4) NOT NULL,

    currency_code VARCHAR(3) NOT NULL DEFAULT 'INR',

    starts_at TIMESTAMPTZ NOT NULL,

    current_period_start TIMESTAMPTZ NOT NULL,

    current_period_end TIMESTAMPTZ NOT NULL,

    trial_starts_at TIMESTAMPTZ,

    trial_ends_at TIMESTAMPTZ,

    cancelled_at TIMESTAMPTZ,

    cancellation_reason TEXT,

    ended_at TIMESTAMPTZ,

    provider platform_payment_provider,

    provider_subscription_id VARCHAR(255),

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- -------------------------------------------------
    -- Foreign Keys
    -- -------------------------------------------------

    CONSTRAINT fk_platform_subscriptions_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_subscriptions_plan
        FOREIGN KEY (plan_id)
        REFERENCES platform_plans(id)
        ON DELETE RESTRICT,

    -- -------------------------------------------------
    -- Monetary validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_subscriptions_price
        CHECK (price >= 0),

    -- -------------------------------------------------
    -- Currency validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_subscriptions_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),

    -- -------------------------------------------------
    -- Billing period validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_subscriptions_period
        CHECK (
            current_period_end > current_period_start
        ),

    -- -------------------------------------------------
    -- Trial validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_subscriptions_trial_period
        CHECK (
            trial_ends_at IS NULL
            OR trial_starts_at IS NULL
            OR trial_ends_at >= trial_starts_at
        ),

    -- -------------------------------------------------
    -- Cancellation validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_subscriptions_cancellation
        CHECK (
            cancelled_at IS NULL
            OR cancelled_at >= starts_at
        ),

    -- -------------------------------------------------
    -- End validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_subscriptions_ended
        CHECK (
            ended_at IS NULL
            OR ended_at >= starts_at
        )
);

COMMENT ON TABLE platform_subscriptions IS
'Represents an organization subscription to a platform plan and tracks its billing and lifecycle state.';

COMMENT ON COLUMN platform_subscriptions.price IS
'Price locked for this subscription. This preserves the historical subscription price even if the underlying plan price changes later.';

COMMENT ON COLUMN platform_subscriptions.current_period_start IS
'Beginning of the current subscription billing period.';

COMMENT ON COLUMN platform_subscriptions.current_period_end IS
'End of the current subscription billing period.';

COMMENT ON COLUMN platform_subscriptions.provider_subscription_id IS
'Subscription identifier returned by the external payment provider, when applicable.';

COMMENT ON COLUMN platform_subscriptions.metadata IS
'Optional subscription metadata for billing integrations and future configuration.';

-- =====================================================
-- Indexes : platform_subscriptions
-- =====================================================

CREATE INDEX idx_platform_subscriptions_organization
ON platform_subscriptions(organization_id);

CREATE INDEX idx_platform_subscriptions_plan
ON platform_subscriptions(plan_id);

CREATE INDEX idx_platform_subscriptions_status
ON platform_subscriptions(status);

CREATE INDEX idx_platform_subscriptions_period_end
ON platform_subscriptions(current_period_end);

CREATE INDEX idx_platform_subscriptions_provider
ON platform_subscriptions(provider);

CREATE INDEX idx_platform_subscriptions_provider_subscription
ON platform_subscriptions(provider_subscription_id);

-- -----------------------------------------------------
-- Only one active subscription per organization
-- -----------------------------------------------------

CREATE UNIQUE INDEX uq_platform_subscriptions_active_org
ON platform_subscriptions(organization_id)
WHERE status IN ('TRIAL', 'ACTIVE', 'PAST_DUE')
  AND deleted_at IS NULL;

-- =====================================================
-- PLATFORM BILLING
-- Table : platform_payment_transactions
-- Purpose:
--     Stores individual payment transactions associated
--     with an organization's subscription.
--
--     A subscription may have multiple transactions
--     because payments can be retried, failed, refunded,
--     or partially refunded.
-- =====================================================

CREATE TABLE platform_payment_transactions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    subscription_id UUID,

    payment_provider platform_payment_provider NOT NULL,

    status payment_transaction_status NOT NULL DEFAULT 'INITIATED',

    amount NUMERIC(19,4) NOT NULL,

    currency CHAR(3) NOT NULL DEFAULT 'INR',

    external_transaction_id VARCHAR(255),

    external_order_id VARCHAR(255),

    payment_method VARCHAR(100),

    failure_code VARCHAR(100),

    failure_message TEXT,

    initiated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    processed_at TIMESTAMPTZ,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- -------------------------------------------------
    -- Foreign Keys
    -- -------------------------------------------------

    CONSTRAINT fk_platform_payment_transactions_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_payment_transactions_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES platform_subscriptions(id)
        ON DELETE RESTRICT,

    -- -------------------------------------------------
    -- Amount must be positive
    -- -------------------------------------------------

    CONSTRAINT chk_platform_payment_transactions_amount
        CHECK (amount > 0),

    -- -------------------------------------------------
    -- Currency must be a valid ISO-style 3 character code
    -- -------------------------------------------------

    CONSTRAINT chk_platform_payment_transactions_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    -- -------------------------------------------------
    -- Processed timestamp cannot precede initiation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_payment_transactions_processed_at
        CHECK (
            processed_at IS NULL
            OR processed_at >= initiated_at
        )
);

COMMENT ON TABLE platform_payment_transactions IS
'Stores individual payment attempts and results for organization subscriptions. Payment transactions are independent from subscription lifecycle.';

COMMENT ON COLUMN platform_payment_transactions.amount IS
'Payment amount in the specified currency. Stored with four decimal places for financial precision.';

COMMENT ON COLUMN platform_payment_transactions.external_transaction_id IS
'Transaction/payment identifier returned by the external payment provider.';

COMMENT ON COLUMN platform_payment_transactions.external_order_id IS
'Order identifier created by the external payment provider before payment completion.';

COMMENT ON COLUMN platform_payment_transactions.payment_method IS
'Payment method used for the transaction, such as CARD, UPI, NETBANKING, or WALLET.';

COMMENT ON COLUMN platform_payment_transactions.failure_code IS
'Provider-specific failure code when the transaction fails.';

COMMENT ON COLUMN platform_payment_transactions.failure_message IS
'Provider failure message captured for diagnostics and support.';

COMMENT ON COLUMN platform_payment_transactions.metadata IS
'Optional provider-specific transaction metadata.';

-- =====================================================
-- Indexes : platform_payment_transactions
-- =====================================================

CREATE INDEX idx_platform_payment_transactions_organization
ON platform_payment_transactions(organization_id);

CREATE INDEX idx_platform_payment_transactions_subscription
ON platform_payment_transactions(subscription_id);

CREATE INDEX idx_platform_payment_transactions_status
ON platform_payment_transactions(status);

CREATE INDEX idx_platform_payment_transactions_provider
ON platform_payment_transactions(payment_provider);

CREATE INDEX idx_platform_payment_transactions_initiated_at
ON platform_payment_transactions(initiated_at);

CREATE INDEX idx_platform_payment_transactions_external_transaction
ON platform_payment_transactions(external_transaction_id);

CREATE INDEX idx_platform_payment_transactions_external_order
ON platform_payment_transactions(external_order_id);

-- -----------------------------------------------------
-- Provider transaction identifiers should be unique
-- when present.
-- -----------------------------------------------------

CREATE UNIQUE INDEX uq_platform_payment_transactions_external_transaction
ON platform_payment_transactions(
    payment_provider,
    external_transaction_id
)
WHERE external_transaction_id IS NOT NULL
  AND deleted_at IS NULL;

-- -----------------------------------------------------
-- Provider order identifiers should be unique
-- when present.
-- -----------------------------------------------------

CREATE UNIQUE INDEX uq_platform_payment_transactions_external_order
ON platform_payment_transactions(
    payment_provider,
    external_order_id
)
WHERE external_order_id IS NOT NULL
  AND deleted_at IS NULL;

-- =====================================================
-- PLATFORM BILLING
-- Table : platform_invoices
-- Purpose:
--     Represents a billing document issued to an
--     organization for a subscription period.
--
--     Invoices are independent financial records.
--     Payment transactions may reference an invoice.
-- =====================================================

CREATE TABLE platform_invoices (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    organization_id UUID NOT NULL,

    subscription_id UUID,

    invoice_number VARCHAR(50) NOT NULL,

    status platform_invoice_status NOT NULL DEFAULT 'DRAFT',

    currency CHAR(3) NOT NULL DEFAULT 'INR',

    subtotal NUMERIC(19,4) NOT NULL,

    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    total_amount NUMERIC(19,4) NOT NULL,

    amount_paid NUMERIC(19,4) NOT NULL DEFAULT 0,

    amount_due NUMERIC(19,4) NOT NULL,

    issue_date DATE,

    due_date DATE,

    period_start DATE,

    period_end DATE,

    paid_at TIMESTAMPTZ,

    voided_at TIMESTAMPTZ,

    notes TEXT,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- -------------------------------------------------
    -- Foreign Keys
    -- -------------------------------------------------

    CONSTRAINT fk_platform_invoices_organization
        FOREIGN KEY (organization_id)
        REFERENCES platform_organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_platform_invoices_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES platform_subscriptions(id)
        ON DELETE RESTRICT,

    -- -------------------------------------------------
    -- Invoice number
    -- -------------------------------------------------

    CONSTRAINT uq_platform_invoices_number
        UNIQUE (invoice_number),

    -- -------------------------------------------------
    -- Monetary consistency
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoices_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_platform_invoices_tax
        CHECK (tax_amount >= 0),

    CONSTRAINT chk_platform_invoices_discount
        CHECK (discount_amount >= 0),

    CONSTRAINT chk_platform_invoices_total
        CHECK (total_amount >= 0),

    CONSTRAINT chk_platform_invoices_amount_paid
        CHECK (amount_paid >= 0),

    CONSTRAINT chk_platform_invoices_amount_due
        CHECK (amount_due >= 0),

    CONSTRAINT chk_platform_invoices_payment_consistency
        CHECK (
            amount_paid + amount_due = total_amount
        ),

    -- -------------------------------------------------
    -- Currency
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoices_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    -- -------------------------------------------------
    -- Billing period
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoices_period
        CHECK (
            period_end IS NULL
            OR period_start IS NULL
            OR period_end >= period_start
        ),

    -- -------------------------------------------------
    -- Due date cannot precede issue date
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoices_due_date
        CHECK (
            due_date IS NULL
            OR issue_date IS NULL
            OR due_date >= issue_date
        ),

    -- -------------------------------------------------
    -- Paid timestamp consistency
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoices_paid_at
        CHECK (
            paid_at IS NULL
            OR issue_date IS NULL
            OR paid_at::DATE >= issue_date
        ),

    -- -------------------------------------------------
    -- Voided timestamp consistency
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoices_voided_at
        CHECK (
            voided_at IS NULL
            OR issue_date IS NULL
            OR voided_at::DATE >= issue_date
        )
);

COMMENT ON TABLE platform_invoices IS
'Billing documents issued to organizations for subscription charges.';

COMMENT ON COLUMN platform_invoices.invoice_number IS
'Human-readable unique invoice number issued by the OmniHealth platform.';

COMMENT ON COLUMN platform_invoices.amount_paid IS
'Total amount successfully paid against the invoice.';

COMMENT ON COLUMN platform_invoices.amount_due IS
'Remaining amount payable against the invoice.';

COMMENT ON COLUMN platform_invoices.metadata IS
'Optional invoice metadata such as tax, provider, or integration information.';

-- =====================================================
-- Indexes : platform_invoices
-- =====================================================

CREATE INDEX idx_platform_invoices_organization
ON platform_invoices(organization_id);

CREATE INDEX idx_platform_invoices_subscription
ON platform_invoices(subscription_id);

CREATE INDEX idx_platform_invoices_status
ON platform_invoices(status);

CREATE INDEX idx_platform_invoices_issue_date
ON platform_invoices(issue_date);

CREATE INDEX idx_platform_invoices_due_date
ON platform_invoices(due_date);

CREATE INDEX idx_platform_invoices_period
ON platform_invoices(period_start, period_end);

CREATE INDEX idx_platform_invoices_outstanding
ON platform_invoices(
    organization_id,
    due_date
)
WHERE amount_due > 0
  AND status IN (
      'ISSUED',
      'PARTIALLY_PAID',
      'OVERDUE'
  )
  AND deleted_at IS NULL;

-- =====================================================
-- PLATFORM BILLING
-- Table : platform_invoice_items
-- Purpose:
--     Stores individual line items belonging to an
--     invoice.
-- =====================================================

CREATE TABLE platform_invoice_items (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    invoice_id UUID NOT NULL,

    description VARCHAR(500) NOT NULL,

    quantity NUMERIC(19,4) NOT NULL DEFAULT 1,

    unit_price NUMERIC(19,4) NOT NULL,

    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,

    line_total NUMERIC(19,4) NOT NULL,

    metadata JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    -- -------------------------------------------------
    -- Foreign Key
    -- -------------------------------------------------

    CONSTRAINT fk_platform_invoice_items_invoice
        FOREIGN KEY (invoice_id)
        REFERENCES platform_invoices(id)
        ON DELETE RESTRICT,

    -- -------------------------------------------------
    -- Monetary / quantity validation
    -- -------------------------------------------------

    CONSTRAINT chk_platform_invoice_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_platform_invoice_items_unit_price
        CHECK (unit_price >= 0),

    CONSTRAINT chk_platform_invoice_items_discount
        CHECK (discount_amount >= 0),

    CONSTRAINT chk_platform_invoice_items_tax
        CHECK (tax_amount >= 0),

    CONSTRAINT chk_platform_invoice_items_line_total
        CHECK (line_total >= 0)
);

COMMENT ON TABLE platform_invoice_items IS
'Individual billable line items belonging to a platform invoice.';

COMMENT ON COLUMN platform_invoice_items.description IS
'Human-readable description of the billed item or service.';

COMMENT ON COLUMN platform_invoice_items.quantity IS
'Quantity of the billed item.';

COMMENT ON COLUMN platform_invoice_items.unit_price IS
'Price per unit before discount and tax.';

COMMENT ON COLUMN platform_invoice_items.line_total IS
'Final total amount for this invoice line after applicable discount and tax.';

COMMENT ON COLUMN platform_invoice_items.metadata IS
'Optional structured metadata associated with the invoice line item.';

-- =====================================================
-- Indexes : platform_invoice_items
-- =====================================================

CREATE INDEX idx_platform_invoice_items_invoice
ON platform_invoice_items(invoice_id);

CREATE INDEX idx_platform_invoice_items_created_at
ON platform_invoice_items(created_at);