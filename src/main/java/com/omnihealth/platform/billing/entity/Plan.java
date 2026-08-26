package com.omnihealth.platform.billing.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.billing.enums.BillingCycle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A reusable subscription plan offered by the platform.
 * <p>
 * Plans are product definitions and are independent from the per-organization
 * {@link Subscription} that references them. Maps to {@code platform_plans}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "platform_plans")
public class Plan extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "billing_cycle", nullable = false, columnDefinition = "platform_billing_cycle")
    private BillingCycle billingCycle;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "trial_days", nullable = false)
    private Integer trialDays;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
