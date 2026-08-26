package com.omnihealth.platform.tenant.repository;

import com.omnihealth.platform.tenant.entity.TenantDatabase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantDatabaseRepository extends JpaRepository<TenantDatabase, UUID> {

    /**
     * Finds the active (non soft-deleted) tenant database for an organization.
     */
    Optional<TenantDatabase> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);
}
