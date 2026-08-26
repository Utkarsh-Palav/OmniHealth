package com.omnihealth.platform.onboarding.repository;

import com.omnihealth.platform.onboarding.entity.OnboardingSession;
import com.omnihealth.platform.onboarding.enums.OnboardingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, UUID> {

    /**
     * Finds the active onboarding session for an organization.
     * <p>
     * An onboarding session is considered as active when it has not been soft-deleted.
     */
    Optional<OnboardingSession> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    /**
     * Find active onboarding session by its ID
     */
    Optional<OnboardingSession> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Find all active onboarding session by given status.
     */
    List<OnboardingSession> findAllByStatusAndDeletedAtIsNull(OnboardingStatus status);

    /**
     * Checks weather an organization already has an active onboarding session.
     */
    boolean existsByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    /**
     * Find active onboarding sessions that are currently in one of the supplied statuses.
     */
    List<OnboardingSession> findAllByStatusInAndDeletedAtIsNull(List<OnboardingStatus> statuses);

    /**
     * Counts active onboarding sessions by status
     */
    long countByStatusAndDeletedAtIsNull(OnboardingStatus status);

    /**
     * Finds the active onboarding session initiated by a given user that is
     * currently in the supplied status.
     * <p>
     * Used by the email-verification event listener to advance the session
     * that a freshly verified owner started during signup.
     */
    Optional<OnboardingSession> findByInitiatedByIdAndStatusAndDeletedAtIsNull(
            UUID userId,
            OnboardingStatus status
    );

}
