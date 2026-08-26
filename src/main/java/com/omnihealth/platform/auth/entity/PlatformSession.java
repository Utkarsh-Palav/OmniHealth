package com.omnihealth.platform.auth.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "platform_sessions",
        indexes = {
                @Index(name = "idx_platform_sessions_user", columnList = "platform_user_id"),
                @Index(name = "idx_platform_sessions_expires_at", columnList = "expires_at"),
                @Index(name = "idx_platform_sessions_last_used_at", columnList = "last_used_at"),
                @Index(name = "idx_platform_sessions_device", columnList = "device_id")
        }
)
public class PlatformSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, length = 128, unique = true)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Postgres INET column: bind via the inet JDBC type, otherwise Hibernate
    // sends the String as VARCHAR and Postgres rejects the implicit cast
    // ("column ip_address is of type inet but expression is of type character varying").
    // columnDefinition alone only satisfies schema validation, not the write binding.
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "device_id", length = 225)
    private String deviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public boolean isActive() {
        return revokedAt == null
                && getDeletedAt() == null
                && expiresAt != null
                && expiresAt.isAfter(Instant.now());
    }
}
