package com.omnihealth.platform.onboarding.service;

import com.omnihealth.platform.onboarding.dto.request.CompletePaymentRequest;
import com.omnihealth.platform.onboarding.dto.request.SignupRequest;
import com.omnihealth.platform.onboarding.dto.response.OnboardingSessionResponse;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;

import java.util.UUID;

/**
 * Orchestrates the self-service organization onboarding state machine.
 *
 * <p>
 * The onboarding module is the single owner of the {@code OnboardingSession}
 * lifecycle. It delegates the heavy lifting (account creation, organization
 * creation and updates) to the existing user and organization services, and
 * advances the session through the signup flow:
 * </p>
 *
 * <pre>
 * signup            → ACCOUNT_CREATED
 * verify email      → EMAIL_VERIFIED       (via {@link #onEmailVerified(UUID)})
 * complete profile  → ORGANIZATION_CREATED
 * provision tenant  → TENANT_PROVISIONING  (simulated)
 * start trial       → TRIAL_STARTED
 * (complete payment → PAYMENT_COMPLETED)   (optional)
 * activate          → ACTIVE
 * </pre>
 */
public interface OnboardingService {

    /**
     * Performs a minimal self-service signup: creates the owner account and a
     * draft organization, then opens an onboarding session at
     * {@code ACCOUNT_CREATED}.
     *
     * @param request owner and company details
     * @return the newly created onboarding session
     */
    OnboardingSessionResponse signup(SignupRequest request);

    /**
     * Advances the session started by the given user from
     * {@code ACCOUNT_CREATED} to {@code EMAIL_VERIFIED}.
     *
     * <p>
     * Invoked from within the email-verification transaction via an application
     * event. This method is intentionally defensive: if no matching session is
     * found, or it is not in the expected state, it logs and returns without
     * throwing so that email verification itself is never rolled back.
     * </p>
     *
     * @param userId the user whose email was just verified
     */
    void onEmailVerified(UUID userId);

    /**
     * Completes the organization profile and advances the session from
     * {@code EMAIL_VERIFIED} to {@code ORGANIZATION_CREATED}.
     */
    OnboardingSessionResponse completeOrganizationProfile(
            UUID sessionId,
            UpdateOrganizationRequest request
    );

    /**
     * Provisions the tenant database (simulated) and advances the session from
     * {@code ORGANIZATION_CREATED} to {@code TENANT_PROVISIONING}.
     */
    OnboardingSessionResponse provisionTenant(UUID sessionId);

    /**
     * Starts the trial subscription and advances the session from
     * {@code TENANT_PROVISIONING} to {@code TRIAL_STARTED}.
     */
    OnboardingSessionResponse startTrial(UUID sessionId);

    /**
     * Records an optional payment and advances the session from
     * {@code TRIAL_STARTED} to {@code PAYMENT_COMPLETED}.
     */
    OnboardingSessionResponse completePayment(
            UUID sessionId,
            CompletePaymentRequest request
    );

    /**
     * Activates the organization and completes onboarding, advancing the
     * session from {@code TRIAL_STARTED} or {@code PAYMENT_COMPLETED} to
     * {@code ACTIVE}.
     */
    OnboardingSessionResponse activate(UUID sessionId);

    /**
     * Returns the current state of an onboarding session by its identifier.
     */
    OnboardingSessionResponse getSession(UUID sessionId);

    /**
     * Returns the current state of the active onboarding session for an
     * organization.
     */
    OnboardingSessionResponse getSessionByOrganization(UUID organizationId);
}
