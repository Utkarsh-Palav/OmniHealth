package com.omnihealth.platform.organization.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "platform_organization_memberships",
        indexes = {
                @Index(name = "idx_platform_org_memberships_user", columnList = "platform_user_id"),
                @Index(name = "idx_platform_org_memberships_organization", columnList = "organization_id")
        }
)
public class PlatformOrganizationMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_user_id", nullable = false)
    private User user;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;
}
