package com.omnihealth.platform.billing.repository;

import com.omnihealth.platform.billing.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Finds the active (non soft-deleted) subscription for an organization.
     */
    Optional<Subscription> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
