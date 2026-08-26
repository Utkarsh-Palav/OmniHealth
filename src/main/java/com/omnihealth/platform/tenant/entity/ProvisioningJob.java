package com.omnihealth.platform.tenant.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.tenant.enums.ProvisioningJobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Tracks an asynchronous tenant-provisioning job for an organization.
 * <p>
 * Provisioning is currently simulated synchronously; this table records the
 * job outcome and is the extension point for real, out-of-band provisioning.
 * Maps to {@code platform_provisioning_jobs}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "platform_provisioning_jobs")
public class ProvisioningJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_database_id")
    private TenantDatabase tenantDatabase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "platform_provisioning_job_status")
    private ProvisioningJobStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
