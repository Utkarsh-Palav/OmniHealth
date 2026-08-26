package com.omnihealth.platform.auth;

import com.omnihealth.common.enums.TokenType;
import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.platform.auth.dto.request.LoginRequest;
import com.omnihealth.platform.auth.service.AuthService;
import com.omnihealth.platform.billing.repository.SubscriptionRepository;
import com.omnihealth.platform.onboarding.dto.request.SignupRequest;
import com.omnihealth.platform.onboarding.dto.response.OnboardingSessionResponse;
import com.omnihealth.platform.onboarding.enums.OnboardingStatus;
import com.omnihealth.platform.onboarding.service.OnboardingService;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.entity.OrganizationType;
import com.omnihealth.platform.organization.entity.PlatformOrganizationMembership;
import com.omnihealth.platform.organization.repository.OrganizationRepository;
import com.omnihealth.platform.organization.repository.PlatformOrganizationMembershipRepository;
import com.omnihealth.platform.tenant.repository.TenantDatabaseRepository;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.entity.VerificationToken;
import com.omnihealth.platform.user.event.EmailVerifiedEvent;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.repository.VerificationTokenRepository;
import com.omnihealth.platform.user.service.UserService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Integration test requiring live PostgreSQL container on localhost:5432")
class AuthenticationFlowIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PlatformOrganizationMembershipRepository membershipRepository;

    @Autowired
    private TenantDatabaseRepository tenantDatabaseRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    @Transactional
    void testCompleteOnboardingJourneyFlow() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner_" + uniqueSuffix + "@omnihealth.test";
        String password = "StrongPassword123!";

        // ---------------------------------------------------------------
        // STEP 1: Self-service signup.
        // Creates the owner account (PENDING_EMAIL_VERIFICATION), a draft
        // organization with the owner as primary member, and the onboarding
        // session at ACCOUNT_CREATED.
        // ---------------------------------------------------------------
        SignupRequest signupRequest = new SignupRequest(
                "Utkarsh",
                "Palav",
                email,
                password,
                "Omni Clinic " + uniqueSuffix,
                OrganizationType.CLINIC
        );

        OnboardingSessionResponse signup = onboardingService.signup(signupRequest);
        assertNotNull(signup.id());
        assertEquals(OnboardingStatus.ACCOUNT_CREATED, signup.status());
        assertNotNull(signup.organizationId());
        assertNotNull(signup.initiatedByUserId());

        UUID sessionId = signup.id();
        UUID organizationId = signup.organizationId();
        UUID ownerUserId = signup.initiatedByUserId();

        // Owner account is created pending email verification.
        User owner = userRepository.findById(ownerUserId).orElseThrow();
        assertEquals(UserStatus.PENDING_EMAIL_VERIFICATION, owner.getUserStatus());

        // A hashed email-verification token was issued in platform_tokens.
        VerificationToken tokenEntity = verificationTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(ownerUserId)
                        && t.getTokenType() == TokenType.EMAIL_VERIFICATION)
                .findFirst()
                .orElseThrow();
        assertNull(tokenEntity.getConsumedAt());

        // The organization was created in DRAFT with a single primary membership.
        assertEquals(OrganizationStatus.DRAFT,
                organizationRepository.findByIdAndDeletedAtIsNull(organizationId).orElseThrow().getStatus());

        List<PlatformOrganizationMembership> memberships = membershipRepository
                .findAllByUserIdAndDeletedAtIsNullAndLeftAtIsNull(ownerUserId);
        assertEquals(1, memberships.size());
        assertTrue(memberships.get(0).isPrimary());
        assertEquals(organizationId, memberships.get(0).getOrganization().getId());

        // ---------------------------------------------------------------
        // STEP 2: Email verification.
        // In production the verify endpoint marks the token consumed, activates
        // the user, and publishes EmailVerifiedEvent; the onboarding listener
        // then advances the session. The raw token is only delivered by email,
        // so we reproduce the verify side-effects and republish the event.
        // ---------------------------------------------------------------
        tokenEntity.setConsumedAt(Instant.now());
        verificationTokenRepository.save(tokenEntity);

        owner.setUserStatus(UserStatus.ACTIVE);
        owner.setEmailVerifiedAt(Instant.now());
        userRepository.save(owner);

        eventPublisher.publishEvent(new EmailVerifiedEvent(ownerUserId));

        assertEquals(OnboardingStatus.EMAIL_VERIFIED,
                onboardingService.getSession(sessionId).status());

        // ---------------------------------------------------------------
        // STEP 3: Complete organization profile.
        // ---------------------------------------------------------------
        UpdateOrganizationRequest profile = UpdateOrganizationRequest.builder()
                .registeredAddressLine1("123 Healthcare Ave")
                .registeredCity("Mumbai")
                .registeredState("Maharashtra")
                .registeredPostalCode("400001")
                .registeredCountryCode("IN")
                .websiteUrl("https://omniclinic.test")
                .build();

        OnboardingSessionResponse afterProfile =
                onboardingService.completeOrganizationProfile(sessionId, profile);
        assertEquals(OnboardingStatus.ORGANIZATION_CREATED, afterProfile.status());
        assertNotNull(afterProfile.organizationCompletedAt());

        // ---------------------------------------------------------------
        // STEP 4: Provision tenant (simulated) — writes tenant_databases +
        // platform_provisioning_jobs rows.
        // ---------------------------------------------------------------
        OnboardingSessionResponse afterProvision = onboardingService.provisionTenant(sessionId);
        assertEquals(OnboardingStatus.TENANT_PROVISIONING, afterProvision.status());
        assertNotNull(afterProvision.tenantProvisioningStartedAt());
        assertTrue(tenantDatabaseRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId).isPresent());

        // ---------------------------------------------------------------
        // STEP 5: Start trial — writes a TRIAL subscription on the default plan.
        // ---------------------------------------------------------------
        OnboardingSessionResponse afterTrial = onboardingService.startTrial(sessionId);
        assertEquals(OnboardingStatus.TRIAL_STARTED, afterTrial.status());
        assertNotNull(afterTrial.trialStartedAt());
        assertTrue(subscriptionRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId).isPresent());

        // ---------------------------------------------------------------
        // STEP 6: Activate — organization goes ACTIVE, session completes.
        // ---------------------------------------------------------------
        OnboardingSessionResponse activated = onboardingService.activate(sessionId);
        assertEquals(OnboardingStatus.ACTIVE, activated.status());
        assertNotNull(activated.completedAt());
        assertEquals(OrganizationStatus.ACTIVE,
                organizationRepository.findByIdAndDeletedAtIsNull(organizationId).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void testLoginAccountLockoutAfterMaxFailures() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "lockout_" + uniqueSuffix + "@omnihealth.test";
        String password = "CorrectPassword123!";

        CreateUserRequest userRequest = new CreateUserRequest(
                "Jane",
                null,
                "Doe",
                null,
                email,
                null,
                null,
                null,
                password
        );
        UserResponse createdUser = userService.createUser(userRequest);

        // Activate user
        User user = userRepository.findById(createdUser.id()).orElseThrow();
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 5 consecutive failed login attempts
        for (int i = 0; i < 5; i++) {
            LoginRequest badRequest = new LoginRequest(email, "WrongPassword!", false);
            assertThrows(Exception.class, () -> authService.login(badRequest, request, response));
        }

        User lockedUser = userRepository.findById(createdUser.id()).orElseThrow();
        assertNotNull(lockedUser.getLockedUntil());
        assertTrue(lockedUser.getLockedUntil().isAfter(Instant.now()));

        // Even with correct password, login fails while locked
        LoginRequest correctRequest = new LoginRequest(email, password, false);
        assertThrows(Exception.class, () -> authService.login(correctRequest, request, response));
    }
}
