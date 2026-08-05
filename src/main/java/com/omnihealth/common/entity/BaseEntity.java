package com.omnihealth.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity for all persistent entities.
 * <p>
 * Provides:
 * - UUID primary key
 * - Audit timestamps
 * - Optimistic locking
 */
@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    // Primary Key.
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    // Timestamps when the entity was created.
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Timestamps when the entity was modified.
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    // Soft-delete timestamp
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Optimistic locking version
    @Version
    @Column(nullable = false)
    private long version;
}
