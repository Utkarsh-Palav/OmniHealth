package com.omnihealth.platform.tenant.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.tenant.enums.TenantDatabaseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Tracks the provisioned tenant database connection for an organization.
 * <p>
 * Each organization gets an isolated database for its operational healthcare
 * data. Maps to {@code tenant_databases}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tenant_databases")
public class TenantDatabase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "tenant_database_status")
    private TenantDatabaseStatus status;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(name = "connection_pool_size", nullable = false)
    private Integer connectionPoolSize;

    @Column(name = "provisioned_at")
    private Instant provisionedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
