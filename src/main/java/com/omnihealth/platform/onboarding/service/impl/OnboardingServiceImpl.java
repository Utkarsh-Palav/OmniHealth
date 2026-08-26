package com.omnihealth.platform.onboarding.service.impl;

import com.omnihealth.config.persistence.TenantProvisioningProperties;
import com.omnihealth.platform.billing.entity.Plan;
import com.omnihealth.platform.billing.entity.Subscription;
import com.omnihealth.platform.billing.enums.SubscriptionStatus;
import com.omnihealth.platform.billing.repository.PlanRepository;
import com.omnihealth.platform.billing.repository.SubscriptionRepository;
import com.omnihealth.platform.onboarding.dto.request.CompletePaymentRequest;
import com.omnihealth.platform.onboarding.dto.request.SignupRequest;
import com.omnihealth.platform.onboarding.dto.response.OnboardingSessionResponse;
import com.omnihealth.platform.onboarding.entity.OnboardingSession;
import com.omnihealth.platform.onboarding.enums.OnboardingStatus;
import com.omnihealth.platform.onboarding.exception.OnboardingErrorCode;
import com.omnihealth.platform.onboarding.exception.OnboardingException;
import com.omnihealth.platform.onboarding.exception.OnboardingNotFoundException;
import com.omnihealth.platform.onboarding.mapper.OnboardingMapper;
import com.omnihealth.platform.onboarding.repository.OnboardingSessionRepository;
import com.omnihealth.platform.onboarding.service.OnboardingService;
import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.repository.OrganizationRepository;
import com.omnihealth.platform.organization.service.OrganizationService;
import com.omnihealth.platform.tenant.entity.ProvisioningJob;
import com.omnihealth.platform.tenant.entity.TenantDatabase;
import com.omnihealth.platform.tenant.enums.ProvisioningJobStatus;
import com.omnihealth.platform.tenant.enums.TenantDatabaseStatus;
import com.omnihealth.platform.tenant.provisioning.TenantProvisioningService;
import com.omnihealth.platform.tenant.repository.ProvisioningJobRepository;
import com.omnihealth.platform.tenant.repository.TenantDatabaseRepository;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of the organization onboarding orchestrator.
 *
 * <p>
 * This service is the single owner of the {@link OnboardingSession} lifecycle.
 * It delegates account and organization persistence to the existing user and
 * organization services and writes the real billing/tenant rows as the flow
 * progresses. {@link #provisionTenant(UUID)} enqueues real, asynchronous tenant
 * database provisioning (create database + apply {@code db/tenant/migration})
 * that runs after the transaction commits and flips the tenant/job rows to
 * READY/COMPLETED (or FAILED with a reason).
 * </p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    // --- Signup defaults (quick signup fills the rest during complete-profile) ---
    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    private static final String DEFAULT_CURRENCY_CODE = "INR";
    private static final String DEFAULT_LOCALE = "en-IN";

    // --- Organization code generation ---
    private static final int MAX_SLUG_LENGTH = 40;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int CODE_SUFFIX_LENGTH = 8;

    // --- Trial plan ---
    private static final String TRIAL_PLAN_CODE = "TRIAL";

    // --- Tenant database defaults (host/port/schema come from app.tenant.connection.*) ---
    private static final String TENANT_DB_PREFIX = "omnihealth_tenant_";
    private static final int DEFAULT_TENANT_DB_POOL_SIZE = 10;

    // --- current_step hints: the next endpoint the client should call ---
    private static final String STEP_EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String STEP_ORGANIZATION_PROFILE = "ORGANIZATION_PROFILE";
    private static final String STEP_TENANT_PROVISIONING = "TENANT_PROVISIONING";
    private static final String STEP_PLAN_SELECTION = "PLAN_SELECTION";
    private static final String STEP_PAYMENT = "PAYMENT";
    private static final String STEP_ACTIVATION = "ACTIVATION";
    private static final String STEP_COMPLETED = "COMPLETED";

    private final OnboardingSessionRepository onboardingSessionRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantDatabaseRepository tenantDatabaseRepository;
    private final ProvisioningJobRepository provisioningJobRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantProvisioningProperties tenantProvisioningProperties;
    private final UserService userService;
    private final OrganizationService organizationService;
    private final OnboardingMapper onboardingMapper;

    @Override
    public OnboardingSessionResponse signup(SignupRequest request) {
        log.info("Starting self-service signup for email={}", request.email());

        // 1. Create the owner account. This issues a hashed email-verification
        //    token and sends the verification email after the transaction commits.
        //    A duplicate email throws here and rolls back the whole signup.
        CreateUserRequest createUserRequest = new CreateUserRequest(
                request.firstName(),
                null,
                request.lastName(),
                null,
                request.email(),
                null,
                null,
                null,
                request.password()
        );
        UserResponse owner = userService.createUser(createUserRequest);

        // 2. Create the draft organization, with the new user as primary member.
        CreateOrganizationRequest organizationRequest = CreateOrganizationRequest.builder()
                .organizationCode(generateOrganizationCode(request.displayName()))
                .legalName(request.displayName())
                .displayName(request.displayName())
                .organizationType(request.organizationType())
                .officialEmail(request.email())
                .timezone(DEFAULT_TIMEZONE)
                .currencyCode(DEFAULT_CURRENCY_CODE)
                .locale(DEFAULT_LOCALE)
                .build();
        OrganizationResponse organization =
                organizationService.createOrganization(organizationRequest, owner.id());

        // 3. Open the onboarding session at ACCOUNT_CREATED.
        OnboardingSession session = OnboardingSession.builder()
                .organization(organizationRepository.getReferenceById(organization.id()))
                .initiatedBy(userRepository.getReferenceById(owner.id()))
                .status(OnboardingStatus.ACCOUNT_CREATED)
                .currentStep(STEP_EMAIL_VERIFICATION)
                .startedAt(Instant.now())
                .build();
        OnboardingSession saved = onboardingSessionRepository.save(session);

        log.info(
                "Signup complete. onboardingSessionId={}, organizationId={}, ownerUserId={}",
                saved.getId(), organization.id(), owner.id()
        );

        return onboardingMapper.toResponse(saved);
    }

    @Override
    public void onEmailVerified(UUID userId) {
        // Runs inside the email-verification transaction. Must never throw:
        // a failure here would roll back the verification itself.
        Optional<OnboardingSession> maybeSession = onboardingSessionRepository
                .findByInitiatedByIdAndStatusAndDeletedAtIsNull(
                        userId, OnboardingStatus.ACCOUNT_CREATED);

        if (maybeSession.isEmpty()) {
            log.debug(
                    "No ACCOUNT_CREATED onboarding session for userId={}; " +
                            "skipping email-verified transition", userId
            );
            return;
        }

        OnboardingSession session = maybeSession.get();
        Instant now = Instant.now();
        session.setStatus(OnboardingStatus.EMAIL_VERIFIED);
        session.setEmailVerifiedAt(now);
        session.setCurrentStep(STEP_ORGANIZATION_PROFILE);
        onboardingSessionRepository.save(session);

        log.info(
                "Onboarding session id={} advanced to EMAIL_VERIFIED for userId={}",
                session.getId(), userId
        );
    }

    @Override
    public OnboardingSessionResponse completeOrganizationProfile(
            UUID sessionId,
            UpdateOrganizationRequest request
    ) {
        OnboardingSession session = getSessionOrThrow(sessionId);
        requireStatus(session, OnboardingStatus.EMAIL_VERIFIED);

        organizationService.updateOrganization(session.getOrganization().getId(), request);

        Instant now = Instant.now();
        session.setStatus(OnboardingStatus.ORGANIZATION_CREATED);
        session.setOrganizationCompletedAt(now);
        session.setCurrentStep(STEP_TENANT_PROVISIONING);

        log.info("Onboarding session id={} advanced to ORGANIZATION_CREATED", sessionId);
        return onboardingMapper.toResponse(onboardingSessionRepository.save(session));
    }

    @Override
    public OnboardingSessionResponse provisionTenant(UUID sessionId) {
        OnboardingSession session = getSessionOrThrow(sessionId);
        requireStatus(session, OnboardingStatus.ORGANIZATION_CREATED);

        Organization organization = session.getOrganization();
        UUID organizationId = organization.getId();
        Instant now = Instant.now();

        TenantProvisioningProperties.Connection connectionConfig = tenantProvisioningProperties.getConnection();
        String databaseName = TENANT_DB_PREFIX + organizationId.toString().replace("-", "");

        // Respect uq_tenant_databases_active_org: never create a second active row
        // for the same organization. Reuse the existing row, resetting it only when
        // a prior attempt FAILED (or was ARCHIVED) so provisioning can be retried.
        Optional<TenantDatabase> existing =
                tenantDatabaseRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);

        TenantDatabase tenantDatabase;
        boolean enqueue;
        if (existing.isEmpty()) {
            tenantDatabase = TenantDatabase.builder()
                    .organization(organization)
                    .status(TenantDatabaseStatus.PENDING)
                    .databaseName(databaseName)
                    .host(connectionConfig.getHost())
                    .port(connectionConfig.getPort())
                    .schemaName(connectionConfig.getSchema())
                    .connectionPoolSize(DEFAULT_TENANT_DB_POOL_SIZE)
                    .build();
            enqueue = true;
        } else {
            tenantDatabase = existing.get();
            switch (tenantDatabase.getStatus()) {
                case READY -> {
                    log.info("Tenant database already READY for organizationId={}; skipping re-provision",
                            organizationId);
                    enqueue = false;
                }
                case PENDING, PROVISIONING -> {
                    log.info("Tenant database provisioning already in flight for organizationId={} (status={}); "
                            + "skipping enqueue", organizationId, tenantDatabase.getStatus());
                    enqueue = false;
                }
                default -> {
                    // FAILED or ARCHIVED: reset the same row and retry provisioning.
                    tenantDatabase.setStatus(TenantDatabaseStatus.PENDING);
                    tenantDatabase.setDatabaseName(databaseName);
                    tenantDatabase.setHost(connectionConfig.getHost());
                    tenantDatabase.setPort(connectionConfig.getPort());
                    tenantDatabase.setSchemaName(connectionConfig.getSchema());
                    tenantDatabase.setConnectionPoolSize(DEFAULT_TENANT_DB_POOL_SIZE);
                    tenantDatabase.setProvisionedAt(null);
                    tenantDatabase.setReadyAt(null);
                    tenantDatabase.setFailedAt(null);
                    tenantDatabase.setFailureReason(null);
                    tenantDatabase.setArchivedAt(null);
                    enqueue = true;
                }
            }
        }
        TenantDatabase savedTenantDatabase = tenantDatabaseRepository.save(tenantDatabase);

        // Advance the session immediately; downstream startTrial/activate key off the
        // SESSION status, so they keep working while the database is built in the
        // background. Provisioning progress lives on the tenant/job rows.
        session.setStatus(OnboardingStatus.TENANT_PROVISIONING);
        session.setTenantProvisioningStartedAt(now);
        session.setCurrentStep(STEP_PLAN_SELECTION);
        OnboardingSessionResponse response =
                onboardingMapper.toResponse(onboardingSessionRepository.save(session));

        if (enqueue) {
            ProvisioningJob provisioningJob = ProvisioningJob.builder()
                    .organization(organization)
                    .tenantDatabase(savedTenantDatabase)
                    .status(ProvisioningJobStatus.QUEUED)
                    .attempts(0)
                    .build();
            ProvisioningJob savedJob = provisioningJobRepository.save(provisioningJob);

            // Fire the worker only after this transaction commits: it reloads the
            // rows by id, so they must be committed and visible first.
            triggerProvisioningAfterCommit(savedJob.getId(), savedTenantDatabase.getId());
        }

        log.info(
                "Onboarding session id={} advanced to TENANT_PROVISIONING; "
                        + "tenant database id={} status={} (enqueued={})",
                sessionId, savedTenantDatabase.getId(), savedTenantDatabase.getStatus(), enqueue
        );
        return response;
    }

    /**
     * Fires the async provisioning worker after the current transaction commits,
     * mirroring the after-commit dispatch in {@code UserServiceImpl}. Falls back
     * to a direct call when no transaction is active.
     */
    private void triggerProvisioningAfterCommit(UUID jobId, UUID tenantDatabaseId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tenantProvisioningService.provisionAsync(jobId, tenantDatabaseId);
                }
            });
        } else {
            tenantProvisioningService.provisionAsync(jobId, tenantDatabaseId);
        }
    }

    @Override
    public OnboardingSessionResponse startTrial(UUID sessionId) {
        OnboardingSession session = getSessionOrThrow(sessionId);
        requireStatus(session, OnboardingStatus.TENANT_PROVISIONING);

        Plan trialPlan = planRepository.findByCodeAndDeletedAtIsNull(TRIAL_PLAN_CODE)
                .orElseThrow(() -> new OnboardingException(
                        OnboardingErrorCode.DEFAULT_TRIAL_PLAN_NOT_FOUND));

        Instant now = Instant.now();
        Instant trialEnd = now.plus(trialPlan.getTrialDays(), ChronoUnit.DAYS);
        // platform_subscriptions enforces current_period_end > current_period_start
        // (strict). Guard the zero-trial-day case so the period is always positive.
        Instant periodEnd = trialEnd.isAfter(now) ? trialEnd : now.plus(1, ChronoUnit.DAYS);

        Subscription subscription = Subscription.builder()
                .organization(session.getOrganization())
                .plan(trialPlan)
                .status(SubscriptionStatus.TRIAL)
                .billingCycle(trialPlan.getBillingCycle())
                .price(trialPlan.getPrice())
                .currencyCode(trialPlan.getCurrencyCode())
                .startsAt(now)
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .trialStartsAt(now)
                .trialEndsAt(trialEnd)
                .createdBy(session.getInitiatedBy())
                .build();
        subscriptionRepository.save(subscription);

        session.setStatus(OnboardingStatus.TRIAL_STARTED);
        session.setTrialStartedAt(now);
        session.setCurrentStep(STEP_PAYMENT);

        log.info(
                "Onboarding session id={} advanced to TRIAL_STARTED on plan={}",
                sessionId, trialPlan.getCode()
        );
        return onboardingMapper.toResponse(onboardingSessionRepository.save(session));
    }

    @Override
    public OnboardingSessionResponse completePayment(
            UUID sessionId,
            CompletePaymentRequest request
    ) {
        OnboardingSession session = getSessionOrThrow(sessionId);
        requireStatus(session, OnboardingStatus.TRIAL_STARTED);

        Instant now = Instant.now();

        // Reflect provider details onto the active subscription when supplied.
        // The full payments/invoicing domain is deferred to a billing module.
        if (request != null && request.provider() != null) {
            subscriptionRepository
                    .findByOrganizationIdAndDeletedAtIsNull(session.getOrganization().getId())
                    .ifPresent(subscription -> {
                        subscription.setProvider(request.provider());
                        subscription.setProviderSubscriptionId(request.providerReference());
                        subscriptionRepository.save(subscription);
                    });
        }

        session.setStatus(OnboardingStatus.PAYMENT_COMPLETED);
        session.setPaymentCompletedAt(now);
        session.setCurrentStep(STEP_ACTIVATION);

        log.info("Onboarding session id={} advanced to PAYMENT_COMPLETED", sessionId);
        return onboardingMapper.toResponse(onboardingSessionRepository.save(session));
    }

    @Override
    public OnboardingSessionResponse activate(UUID sessionId) {
        OnboardingSession session = getSessionOrThrow(sessionId);
        requireStatus(
                session,
                OnboardingStatus.TRIAL_STARTED,
                OnboardingStatus.PAYMENT_COMPLETED
        );

        Instant now = Instant.now();

        Organization organization = organizationRepository
                .findByIdAndDeletedAtIsNull(session.getOrganization().getId())
                .orElseThrow(() -> new OnboardingException(
                        OnboardingErrorCode.INVALID_ONBOARDING_STATE,
                        "Organization not found for onboarding session " + sessionId));
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setActivatedAt(now);
        organizationRepository.save(organization);

        session.setStatus(OnboardingStatus.ACTIVE);
        session.setCompletedAt(now);
        session.setCurrentStep(STEP_COMPLETED);

        log.info(
                "Onboarding session id={} completed; organizationId={} activated",
                sessionId, organization.getId()
        );
        return onboardingMapper.toResponse(onboardingSessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingSessionResponse getSession(UUID sessionId) {
        return onboardingMapper.toResponse(getSessionOrThrow(sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingSessionResponse getSessionByOrganization(UUID organizationId) {
        OnboardingSession session = onboardingSessionRepository
                .findByOrganizationIdAndDeletedAtIsNull(organizationId)
                .orElseThrow(OnboardingNotFoundException::new);
        return onboardingMapper.toResponse(session);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private OnboardingSession getSessionOrThrow(UUID sessionId) {
        return onboardingSessionRepository
                .findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(OnboardingNotFoundException::new);
    }

    private void requireStatus(OnboardingSession session, OnboardingStatus... allowed) {
        for (OnboardingStatus status : allowed) {
            if (session.getStatus() == status) {
                return;
            }
        }
        throw new OnboardingException(
                OnboardingErrorCode.INVALID_ONBOARDING_STATE,
                "Onboarding session " + session.getId() + " is in state "
                        + session.getStatus() + " but this operation requires one of "
                        + Arrays.toString(allowed)
        );
    }

    /**
     * Generates a unique, URL-safe organization code from the display name plus
     * a short random suffix, retrying a handful of times on collision.
     */
    private String generateOrganizationCode(String displayName) {
        String slug = slugify(displayName);

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String suffix = UUID.randomUUID().toString().substring(0, CODE_SUFFIX_LENGTH);
            String code = slug.isBlank() ? suffix : slug + "-" + suffix;

            if (!organizationRepository.existsByOrganizationCode(code)) {
                return code;
            }
        }

        throw new OnboardingException(OnboardingErrorCode.ORGANIZATION_CODE_GENERATION_FAILED);
    }

    private String slugify(String input) {
        if (input == null) {
            return "";
        }

        String slug = input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");

        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }

        return slug;
    }
}
