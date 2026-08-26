package com.omnihealth.platform.organization.repository;

import com.omnihealth.platform.organization.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {
    Optional<Organization> findByOrganizationCode(String organizationCode);

    boolean existsByOrganizationCode(String organizationCode);

    boolean existsByOfficialEmail(String email);

    Optional<Organization> findByIdAndDeletedAtIsNull(UUID id);

    Page<Organization> findAllByDeletedAtIsNull(Pageable pageable);

}
