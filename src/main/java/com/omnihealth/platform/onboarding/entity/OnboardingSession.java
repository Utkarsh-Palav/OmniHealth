package com.omnihealth.platform.onboarding.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.onboarding.enums.OnboardingStatus;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Tracks a single organization's journey through the self-service onboarding
 * state machine. Maps to {@code platform_organization_onboarding}.
 *
 * <p>
 * The {@code status} field is the authoritative state; {@code current_step}
 * is a soft hint describing the next expected client action. Domain lifecycle
 * timestamps ({@code started_at}, {@code email_verified_at}, ...) are distinct
 * from the auditing {@code created_at}/{@code updated_at} inherited from
 * {@link BaseEntity}.
 * </p>
 */
@Entity
@Table(name = "platform_organization_onboarding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user_id")
    private User initiatedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "organization_onboarding_status")
    private OnboardingStatus status;

    @Column(name = "current_step", nullable = false, length = 100)
    private String currentStep;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "organization_completed_at")
    private Instant organizationCompletedAt;

    @Column(name = "tenant_provisioning_started_at")
    private Instant tenantProvisioningStartedAt;

    @Column(name = "trial_started_at")
    private Instant trialStartedAt;

    @Column(name = "payment_completed_at")
    private Instant paymentCompletedAt;

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
