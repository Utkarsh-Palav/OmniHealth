package com.omnihealth.platform.auth.repository;

import com.omnihealth.platform.auth.entity.PlatformSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformSessionRepository extends JpaRepository<PlatformSession, UUID> {

    Optional<PlatformSession> findByRefreshTokenHashAndRevokedAtIsNullAndDeletedAtIsNull(String refreshTokenHash);

    /**
     * Loads an active session together with its owning user in a single query.
     * <p>
     * {@link PlatformSession#getUser()} is a {@code LAZY @ManyToOne}. The
     * authentication filter reads it from OUTSIDE any persistence context
     * (Spring Security filters run before the OSIV scope is bound), so a bare
     * finder returns a detached session whose user proxy can never initialize
     * ({@code LazyInitializationException: no session}). The {@code JOIN FETCH}
     * initializes the user eagerly for this query only — an inner join is
     * correct because {@code user} is {@code optional = false} (NOT NULL FK).
     */
    @Query("""
            SELECT s FROM PlatformSession s
            JOIN FETCH s.user
            WHERE s.refreshTokenHash = :refreshTokenHash
              AND s.revokedAt IS NULL
              AND s.deletedAt IS NULL
            """)
    Optional<PlatformSession> findActiveByRefreshTokenHashWithUser(@Param("refreshTokenHash") String refreshTokenHash);

    List<PlatformSession> findAllByUserIdAndRevokedAtIsNullAndDeletedAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE PlatformSession s SET s.revokedAt = :revokedAt WHERE s.user.id = :userId AND s.revokedAt IS NULL AND s.deletedAt IS NULL")
    void revokeAllByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
