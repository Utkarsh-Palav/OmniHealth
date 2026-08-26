package com.omnihealth.platform.onboarding.service.impl;

import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.platform.billing.entity.Plan;
import com.omnihealth.platform.billing.entity.Subscription;
import com.omnihealth.platform.billing.enums.BillingCycle;
import com.omnihealth.platform.billing.enums.PaymentProvider;
import com.omnihealth.platform.billing.enums.SubscriptionStatus;
import com.omnihealth.platform.billing.repository.PlanRepository;
import com.omnihealth.platform.billing.repository.SubscriptionRepository;
import com.omnihealth.platform.onboarding.dto.request.CompletePaymentRequest;
import com.omnihealth.platform.onboarding.dto.request.SignupRequest;
import com.omnihealth.platform.onboarding.entity.OnboardingSession;
import com.omnihealth.platform.onboarding.enums.OnboardingStatus;
import com.omnihealth.platform.onboarding.exception.OnboardingErrorCode;
import com.omnihealth.platform.onboarding.exception.OnboardingException;
import com.omnihealth.platform.onboarding.exception.OnboardingNotFoundException;
import com.omnihealth.platform.onboarding.mapper.OnboardingMapper;
import com.omnihealth.platform.onboarding.repository.OnboardingSessionRepository;
import com.omnihealth.platform.organization.dto.request.CreateOrganizationRequest;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.dto.response.OrganizationResponse;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.entity.OrganizationType;
import com.omnihealth.platform.organization.repository.OrganizationRepository;
import com.omnihealth.platform.organization.service.OrganizationService;
import com.omnihealth.platform.tenant.entity.ProvisioningJob;
import com.omnihealth.platform.tenant.entity.TenantDatabase;
import com.omnihealth.platform.tenant.enums.ProvisioningJobStatus;
import com.omnihealth.platform.tenant.enums.TenantDatabaseStatus;
import com.omnihealth.platform.tenant.repository.ProvisioningJobRepository;
import com.omnihealth.platform.tenant.repository.TenantDatabaseRepository;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    @Mock
    private OnboardingSessionRepository onboardingSessionRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private TenantDatabaseRepository tenantDatabaseRepository;
    @Mock
    private ProvisioningJobRepository provisioningJobRepository;
    @Mock
    private UserService userService;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private OnboardingMapper onboardingMapper;

    @InjectMocks
    private OnboardingServiceImpl onboardingService;

    // ---------------------------------------------------------------------
    // signup
    // ---------------------------------------------------------------------

    @Test
    void signupCreatesAccountOrganizationAndSession() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        SignupRequest request = new SignupRequest(
                "Utkarsh", "Palav", "owner@omni.test", "Password123!",
                "Omni Clinic", OrganizationType.CLINIC
        );

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(
                UserResponse.builder()
                        .id(userId)
                        .email("owner@omni.test")
                        .status(UserStatus.PENDING_EMAIL_VERIFICATION)
                        .build()
        );
        when(organizationRepository.existsByOrganizationCode(anyString())).thenReturn(false);
        when(organizationService.createOrganization(any(CreateOrganizationRequest.class), eq(userId)))
                .thenReturn(OrganizationResponse.builder()
                        .id(orgId)
                        .organizationType(OrganizationType.CLINIC)
                        .status(OrganizationStatus.DRAFT)
                        .build());

        Organization orgRef = new Organization();
        orgRef.setId(orgId);
        User userRef = new User();
        userRef.setId(userId);
        when(organizationRepository.getReferenceById(orgId)).thenReturn(orgRef);
        when(userRepository.getReferenceById(userId)).thenReturn(userRef);
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingService.signup(request);

        // The owner account request is derived from the signup payload.
        ArgumentCaptor<CreateUserRequest> userRequestCaptor =
                ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createUser(userRequestCaptor.capture());
        CreateUserRequest createUserRequest = userRequestCaptor.getValue();
        assertEquals("Utkarsh", createUserRequest.firstName());
        assertEquals("Palav", createUserRequest.lastName());
        assertEquals("owner@omni.test", createUserRequest.email());
        assertEquals("Password123!", createUserRequest.password());

        // The organization request carries the signup defaults.
        ArgumentCaptor<CreateOrganizationRequest> orgRequestCaptor =
                ArgumentCaptor.forClass(CreateOrganizationRequest.class);
        verify(organizationService).createOrganization(orgRequestCaptor.capture(), eq(userId));
        CreateOrganizationRequest orgRequest = orgRequestCaptor.getValue();
        assertEquals("Omni Clinic", orgRequest.displayName());
        assertEquals("Omni Clinic", orgRequest.legalName());
        assertEquals(OrganizationType.CLINIC, orgRequest.organizationType());
        assertEquals("owner@omni.test", orgRequest.officialEmail());
        assertEquals("Asia/Kolkata", orgRequest.timezone());
        assertEquals("INR", orgRequest.currencyCode());
        assertEquals("en-IN", orgRequest.locale());
        assertNotNull(orgRequest.organizationCode());
        assertFalse(orgRequest.organizationCode().isBlank());

        // The session opens at ACCOUNT_CREATED, linked to the owner and org.
        ArgumentCaptor<OnboardingSession> sessionCaptor =
                ArgumentCaptor.forClass(OnboardingSession.class);
        verify(onboardingSessionRepository).save(sessionCaptor.capture());
        OnboardingSession saved = sessionCaptor.getValue();
        assertEquals(OnboardingStatus.ACCOUNT_CREATED, saved.getStatus());
        assertEquals("EMAIL_VERIFICATION", saved.getCurrentStep());
        assertNotNull(saved.getStartedAt());
        assertEquals(orgId, saved.getOrganization().getId());
        assertEquals(userId, saved.getInitiatedBy().getId());
    }

    // ---------------------------------------------------------------------
    // onEmailVerified
    // ---------------------------------------------------------------------

    @Test
    void onEmailVerifiedAdvancesAccountCreatedSession() {
        UUID userId = UUID.randomUUID();
        OnboardingSession session = OnboardingSession.builder()
                .status(OnboardingStatus.ACCOUNT_CREATED)
                .currentStep("EMAIL_VERIFICATION")
                .startedAt(Instant.now())
                .build();
        session.setId(UUID.randomUUID());

        when(onboardingSessionRepository
                .findByInitiatedByIdAndStatusAndDeletedAtIsNull(userId, OnboardingStatus.ACCOUNT_CREATED))
                .thenReturn(Optional.of(session));
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingService.onEmailVerified(userId);

        assertEquals(OnboardingStatus.EMAIL_VERIFIED, session.getStatus());
        assertNotNull(session.getEmailVerifiedAt());
        assertEquals("ORGANIZATION_PROFILE", session.getCurrentStep());
        verify(onboardingSessionRepository).save(session);
    }

    @Test
    void onEmailVerifiedWithNoSessionIsNoOp() {
        UUID userId = UUID.randomUUID();
        when(onboardingSessionRepository
                .findByInitiatedByIdAndStatusAndDeletedAtIsNull(userId, OnboardingStatus.ACCOUNT_CREATED))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> onboardingService.onEmailVerified(userId));

        verify(onboardingSessionRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // completeOrganizationProfile
    // ---------------------------------------------------------------------

    @Test
    void completeOrganizationProfileAdvancesFromEmailVerified() {
        UUID sessionId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(orgId);
        OnboardingSession session = OnboardingSession.builder()
                .organization(organization)
                .status(OnboardingStatus.EMAIL_VERIFIED)
                .currentStep("ORGANIZATION_PROFILE")
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrganizationRequest update = UpdateOrganizationRequest.builder()
                .registeredCity("Mumbai")
                .build();

        onboardingService.completeOrganizationProfile(sessionId, update);

        verify(organizationService).updateOrganization(orgId, update);
        assertEquals(OnboardingStatus.ORGANIZATION_CREATED, session.getStatus());
        assertNotNull(session.getOrganizationCompletedAt());
        assertEquals("TENANT_PROVISIONING", session.getCurrentStep());
    }

    @Test
    void completeOrganizationProfileFromWrongStateThrows() {
        UUID sessionId = UUID.randomUUID();
        OnboardingSession session = OnboardingSession.builder()
                .status(OnboardingStatus.ACCOUNT_CREATED)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));

        OnboardingException exception = assertThrows(OnboardingException.class, () ->
                onboardingService.completeOrganizationProfile(
                        sessionId, UpdateOrganizationRequest.builder().build()));
        assertEquals(OnboardingErrorCode.INVALID_ONBOARDING_STATE, exception.getErrorCode());
        verify(organizationService, never()).updateOrganization(any(), any());
    }

    // ---------------------------------------------------------------------
    // provisionTenant
    // ---------------------------------------------------------------------

    @Test
    void provisionTenantWritesReadyDatabaseAndCompletedJob() {
        UUID sessionId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(orgId);
        OnboardingSession session = OnboardingSession.builder()
                .organization(organization)
                .status(OnboardingStatus.ORGANIZATION_CREATED)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));
        when(tenantDatabaseRepository.save(any(TenantDatabase.class))).thenAnswer(invocation -> {
            TenantDatabase tenantDatabase = invocation.getArgument(0);
            tenantDatabase.setId(UUID.randomUUID());
            return tenantDatabase;
        });
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingService.provisionTenant(sessionId);

        ArgumentCaptor<TenantDatabase> tenantCaptor = ArgumentCaptor.forClass(TenantDatabase.class);
        verify(tenantDatabaseRepository).save(tenantCaptor.capture());
        TenantDatabase tenantDatabase = tenantCaptor.getValue();
        assertEquals(TenantDatabaseStatus.READY, tenantDatabase.getStatus());
        assertEquals(orgId, tenantDatabase.getOrganization().getId());
        assertEquals(5432, tenantDatabase.getPort());
        assertEquals("public", tenantDatabase.getSchemaName());
        assertNotNull(tenantDatabase.getReadyAt());

        ArgumentCaptor<ProvisioningJob> jobCaptor = ArgumentCaptor.forClass(ProvisioningJob.class);
        verify(provisioningJobRepository).save(jobCaptor.capture());
        ProvisioningJob job = jobCaptor.getValue();
        assertEquals(ProvisioningJobStatus.COMPLETED, job.getStatus());
        assertEquals(1, job.getAttempts());
        assertNotNull(job.getCompletedAt());
        assertEquals(tenantDatabase.getId(), job.getTenantDatabase().getId());

        assertEquals(OnboardingStatus.TENANT_PROVISIONING, session.getStatus());
        assertEquals("PLAN_SELECTION", session.getCurrentStep());
        assertNotNull(session.getTenantProvisioningStartedAt());
    }

    // ---------------------------------------------------------------------
    // startTrial
    // ---------------------------------------------------------------------

    @Test
    void startTrialCreatesTrialSubscription() {
        UUID sessionId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(orgId);
        User owner = new User();
        owner.setId(UUID.randomUUID());
        OnboardingSession session = OnboardingSession.builder()
                .organization(organization)
                .initiatedBy(owner)
                .status(OnboardingStatus.TENANT_PROVISIONING)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        Plan trialPlan = Plan.builder()
                .code("TRIAL")
                .name("Free Trial")
                .billingCycle(BillingCycle.MONTHLY)
                .price(BigDecimal.ZERO)
                .currencyCode("INR")
                .trialDays(14)
                .isPublic(true)
                .isActive(true)
                .sortOrder(0)
                .build();

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));
        when(planRepository.findByCodeAndDeletedAtIsNull("TRIAL")).thenReturn(Optional.of(trialPlan));
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingService.startTrial(sessionId);

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription subscription = subscriptionCaptor.getValue();
        assertEquals(SubscriptionStatus.TRIAL, subscription.getStatus());
        assertEquals(trialPlan, subscription.getPlan());
        assertEquals(BillingCycle.MONTHLY, subscription.getBillingCycle());
        assertEquals("INR", subscription.getCurrencyCode());
        assertEquals(orgId, subscription.getOrganization().getId());
        assertNotNull(subscription.getTrialEndsAt());
        // Honors the strict current_period_end > current_period_start CHECK.
        assertTrue(subscription.getCurrentPeriodEnd().isAfter(subscription.getCurrentPeriodStart()));
        assertEquals(owner.getId(), subscription.getCreatedBy().getId());

        assertEquals(OnboardingStatus.TRIAL_STARTED, session.getStatus());
        assertEquals("PAYMENT", session.getCurrentStep());
        assertNotNull(session.getTrialStartedAt());
    }

    @Test
    void startTrialWithoutDefaultPlanThrows() {
        UUID sessionId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        OnboardingSession session = OnboardingSession.builder()
                .organization(organization)
                .status(OnboardingStatus.TENANT_PROVISIONING)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));
        when(planRepository.findByCodeAndDeletedAtIsNull("TRIAL")).thenReturn(Optional.empty());

        OnboardingException exception =
                assertThrows(OnboardingException.class, () -> onboardingService.startTrial(sessionId));
        assertEquals(OnboardingErrorCode.DEFAULT_TRIAL_PLAN_NOT_FOUND, exception.getErrorCode());
        verify(subscriptionRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // completePayment
    // ---------------------------------------------------------------------

    @Test
    void completePaymentReflectsProviderAndAdvances() {
        UUID sessionId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(orgId);
        OnboardingSession session = OnboardingSession.builder()
                .organization(organization)
                .status(OnboardingStatus.TRIAL_STARTED)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);
        Subscription subscription = Subscription.builder().organization(organization).build();

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));
        when(subscriptionRepository.findByOrganizationIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(subscription));
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingService.completePayment(
                sessionId, new CompletePaymentRequest(PaymentProvider.RAZORPAY, "pay_123"));

        assertEquals(PaymentProvider.RAZORPAY, subscription.getProvider());
        assertEquals("pay_123", subscription.getProviderSubscriptionId());
        verify(subscriptionRepository).save(subscription);
        assertEquals(OnboardingStatus.PAYMENT_COMPLETED, session.getStatus());
        assertEquals("ACTIVATION", session.getCurrentStep());
        assertNotNull(session.getPaymentCompletedAt());
    }

    // ---------------------------------------------------------------------
    // activate
    // ---------------------------------------------------------------------

    @Test
    void activateActivatesOrganizationAndCompletesSession() {
        UUID sessionId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Organization organizationRef = new Organization();
        organizationRef.setId(orgId);
        OnboardingSession session = OnboardingSession.builder()
                .organization(organizationRef)
                .status(OnboardingStatus.TRIAL_STARTED)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        Organization managedOrganization = new Organization();
        managedOrganization.setId(orgId);
        managedOrganization.setStatus(OrganizationStatus.DRAFT);

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));
        when(organizationRepository.findByIdAndDeletedAtIsNull(orgId))
                .thenReturn(Optional.of(managedOrganization));
        when(onboardingSessionRepository.save(any(OnboardingSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingService.activate(sessionId);

        assertEquals(OrganizationStatus.ACTIVE, managedOrganization.getStatus());
        assertNotNull(managedOrganization.getActivatedAt());
        verify(organizationRepository).save(managedOrganization);

        assertEquals(OnboardingStatus.ACTIVE, session.getStatus());
        assertEquals("COMPLETED", session.getCurrentStep());
        assertNotNull(session.getCompletedAt());
    }

    @Test
    void activateFromWrongStateThrows() {
        UUID sessionId = UUID.randomUUID();
        OnboardingSession session = OnboardingSession.builder()
                .status(OnboardingStatus.EMAIL_VERIFIED)
                .startedAt(Instant.now())
                .build();
        session.setId(sessionId);

        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.of(session));

        OnboardingException exception =
                assertThrows(OnboardingException.class, () -> onboardingService.activate(sessionId));
        assertEquals(OnboardingErrorCode.INVALID_ONBOARDING_STATE, exception.getErrorCode());
        verify(organizationRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // getSession
    // ---------------------------------------------------------------------

    @Test
    void getSessionNotFoundThrows() {
        UUID sessionId = UUID.randomUUID();
        when(onboardingSessionRepository.findByIdAndDeletedAtIsNull(sessionId))
                .thenReturn(Optional.empty());

        assertThrows(OnboardingNotFoundException.class, () -> onboardingService.getSession(sessionId));
    }
}
