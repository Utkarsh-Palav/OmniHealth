package com.omnihealth.platform.tenant.repository;

import com.omnihealth.platform.tenant.entity.ProvisioningJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {

    /**
     * Finds the most recent active (non soft-deleted) provisioning job for an organization.
     */
    Optional<ProvisioningJob> findFirstByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);
}
