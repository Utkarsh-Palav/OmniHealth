package com.omnihealth.platform.billing.repository;

import com.omnihealth.platform.billing.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    /**
     * Finds an active (non soft-deleted) plan by its stable machine-readable code.
     */
    Optional<Plan> findByCodeAndDeletedAtIsNull(String code);
}
