package com.omnihealth.platform.billing.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.billing.enums.BillingCycle;
import com.omnihealth.platform.billing.enums.PaymentProvider;
import com.omnihealth.platform.billing.enums.SubscriptionStatus;
import com.omnihealth.platform.organization.entity.Organization;
import com.omnihealth.platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * An organization's subscription to a platform {@link Plan}.
 * <p>
 * The subscription lifecycle is independent from individual payment
 * transactions. Maps to {@code platform_subscriptions}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "platform_subscriptions")
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "platform_subscription_status")
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "billing_cycle", nullable = false, columnDefinition = "platform_billing_cycle")
    private BillingCycle billingCycle;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "trial_starts_at")
    private Instant trialStartsAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "provider", columnDefinition = "platform_payment_provider")
    private PaymentProvider provider;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;
}
