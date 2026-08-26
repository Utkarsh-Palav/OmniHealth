package com.omnihealth.platform.user.entity;

import com.omnihealth.common.entity.BaseEntity;
import com.omnihealth.common.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "platform_users",
        indexes = {
                @Index(name = "idx_platform_users_status", columnList = "status"),
                @Index(name = "idx_platform_users_last_login_at", columnList = "last_login_at"),
                @Index(name = "idx_platform_users_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_platform_users_created_at", columnList = "created_at")
        }
)
public class User extends BaseEntity {

    @Column(
            name = "email",
            nullable = false,
            columnDefinition = "citext"
    )
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "preferred_name", length = 100)
    private String preferredName;

    @Column(name = "phone_country_code", length = 5)
    private String phoneCountryCode;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_image_key", length = 512)
    private String profileImageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private UserStatus userStatus;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private Short failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}