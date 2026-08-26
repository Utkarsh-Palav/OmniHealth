package com.omnihealth.platform.onboarding.dto.response;

import com.omnihealth.platform.onboarding.enums.OnboardingStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API response representing the current state of an organization onboarding
 * session.
 *
 * <p>
 * This DTO intentionally exposes {@code organizationId} and
 * {@code initiatedByUserId} instead of the underlying JPA entities.
 * </p>
 */
public record OnboardingSessionResponse(
        UUID id,
        UUID organizationId,
        UUID initiatedByUserId,
        OnboardingStatus status,
        String currentStep,
        Instant startedAt,
        Instant emailVerifiedAt,
        Instant organizationCompletedAt,
        Instant tenantProvisioningStartedAt,
        Instant trialStartedAt,
        Instant paymentCompletedAt,
        Instant completedAt,
        Instant failedAt,
        String failureReason,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
