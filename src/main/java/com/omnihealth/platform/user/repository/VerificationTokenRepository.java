package com.omnihealth.platform.user.repository;

import com.omnihealth.common.enums.TokenType;
import com.omnihealth.platform.user.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenHashAndTokenTypeAndDeletedAtIsNull(String tokenHash, TokenType tokenType);
}
