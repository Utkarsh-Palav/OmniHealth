package com.omnihealth.tenant.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Maps the {@code application_metadata} table that lives inside every tenant
 * database (created by the tenant baseline migration {@code db/tenant/migration}).
 *
 * <p>Deliberately a plain {@code @Entity} — <strong>not</strong> {@code BaseEntity} —
 * because the tenant table has its own minimal shape ({@code BIGSERIAL} id, a
 * database-defaulted {@code created_at}), unrelated to the platform audit columns.
 * It serves as the routing proof: rows written through the tenant persistence unit
 * must land in the caller's physical tenant database.</p>
 */
@Entity
@Table(name = "application_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "application_name", nullable = false, length = 100)
    private String applicationName;

    @Column(name = "application_version", nullable = false, length = 50)
    private String applicationVersion;

    /** Filled by the column's {@code DEFAULT CURRENT_TIMESTAMP}; never written by the app. */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
