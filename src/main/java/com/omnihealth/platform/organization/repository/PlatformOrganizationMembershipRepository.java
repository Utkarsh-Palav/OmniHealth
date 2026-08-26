package com.omnihealth.platform.organization.repository;

import com.omnihealth.platform.organization.entity.PlatformOrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformOrganizationMembershipRepository extends JpaRepository<PlatformOrganizationMembership, UUID> {

    Optional<PlatformOrganizationMembership> findByOrganizationIdAndUserIdAndDeletedAtIsNull(UUID organizationId, UUID userId);

    List<PlatformOrganizationMembership> findAllByUserIdAndDeletedAtIsNullAndLeftAtIsNull(UUID userId);

    Optional<PlatformOrganizationMembership> findByUserIdAndIsPrimaryTrueAndDeletedAtIsNullAndLeftAtIsNull(UUID userId);

    boolean existsByOrganizationIdAndUserIdAndDeletedAtIsNullAndLeftAtIsNull(UUID organizationId, UUID userId);

    /**
     * Scalar projection of the caller's primary organization id. Returns the
     * {@code UUID} directly (fully materialized) rather than the membership
     * entity, so it is safe to call from a Spring Security filter — no LAZY
     * association is dereferenced outside the persistence context.
     */
    @Query("""
            SELECT m.organization.id
            FROM PlatformOrganizationMembership m
            WHERE m.user.id = :userId
              AND m.isPrimary = true
              AND m.deletedAt IS NULL
              AND m.leftAt IS NULL
            """)
    Optional<UUID> findPrimaryOrganizationIdByUserId(@Param("userId") UUID userId);
}
