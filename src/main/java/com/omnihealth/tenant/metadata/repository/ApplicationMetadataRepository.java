package com.omnihealth.tenant.metadata.repository;

import com.omnihealth.tenant.metadata.entity.ApplicationMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the tenant-local {@code application_metadata} table. Picked up
 * by the tenant {@code @EnableJpaRepositories} (base package
 * {@code com.omnihealth.tenant}) and therefore bound to the tenant EMF/routing.
 */
public interface ApplicationMetadataRepository extends JpaRepository<ApplicationMetadata, Long> {
}
