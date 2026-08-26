package com.omnihealth.platform.auth.service.impl;

import com.omnihealth.common.enums.TokenType;
import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.common.exception.ConflictException;
import com.omnihealth.common.exception.ResourceNotFoundException;
import com.omnihealth.common.exception.UnauthorizedException;
import com.omnihealth.platform.auth.dto.AuthSessionDto;
import com.omnihealth.platform.auth.dto.request.LoginRequest;
import com.omnihealth.platform.auth.dto.response.LoginResponse;
import com.omnihealth.platform.auth.service.AuthService;
import com.omnihealth.platform.auth.service.SessionService;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.entity.VerificationToken;
import com.omnihealth.platform.user.event.EmailVerifiedEvent;
import com.omnihealth.platform.user.mapper.UserMapper;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.repository.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final SessionService sessionService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = request.email() != null ? request.email().trim() : "";

        User user = userRepository.findByEmail(email)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        // Check account lock status
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            log.warn("Login attempt on locked account email={}", email);
            throw new UnauthorizedException("Account is temporarily locked due to multiple failed login attempts. Please try again later.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new UnauthorizedException("Invalid email or password.");
        }

        // Check user lifecycle status
        if (user.getUserStatus() == UserStatus.PENDING_EMAIL_VERIFICATION) {
            throw new UnauthorizedException("Please verify your email address before logging in.");
        }

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Account is suspended or inactive. Please contact support.");
        }

        // Reset failed login state and record login timestamp
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Create authenticated session
        AuthSessionDto sessionDto = sessionService.createSession(user, request.isRememberMe(), httpRequest);

        // Attach HTTP-only secure cookie
        long maxAgeSeconds = Duration.between(Instant.now(), sessionDto.expiresAt()).getSeconds();
        sessionService.attachSessionCookie(httpResponse, sessionDto.rawToken(), maxAgeSeconds);

        log.info("User id={} logged in successfully", user.getId());

        return LoginResponse.builder()
                .user(userMapper.toResponse(user))
                .expiresAt(sessionDto.expiresAt())
                .tokenType("Bearer")
                .build();
    }

    @Override
    public LoginResponse verifyEmailAndAuthenticate(String rawToken, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String tokenHash = hashToken(rawToken);

        VerificationToken token = verificationTokenRepository
                .findByTokenHashAndTokenTypeAndDeletedAtIsNull(tokenHash, TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ResourceNotFoundException("VerificationToken", "token", "Invalid verification token"));

        if (token.getConsumedAt() != null) {
            throw new ConflictException("Email verification token has already been used.");
        }

        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("Verification token has expired or been revoked.");
        }

        // Mark token consumed
        token.setConsumedAt(Instant.now());
        verificationTokenRepository.save(token);

        // Update user state to ACTIVE
        User user = token.getUser();
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        log.info("User id={} email successfully verified and account activated.", user.getId());

        // Notify interested modules (e.g. onboarding) within this transaction.
        eventPublisher.publishEvent(new EmailVerifiedEvent(user.getId()));

        // Automatically establish authenticated session
        AuthSessionDto sessionDto = sessionService.createSession(user, false, httpRequest);
        long maxAgeSeconds = Duration.between(Instant.now(), sessionDto.expiresAt()).getSeconds();
        sessionService.attachSessionCookie(httpResponse, sessionDto.rawToken(), maxAgeSeconds);

        return LoginResponse.builder()
                .user(userMapper.toResponse(user))
                .expiresAt(sessionDto.expiresAt())
                .tokenType("Bearer")
                .build();
    }

    @Override
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawToken = sessionService.extractTokenFromRequest(httpRequest);
        if (rawToken != null) {
            sessionService.revokeSession(rawToken);
        }
        sessionService.clearSessionCookie(httpResponse);
        SecurityContextHolder.clearContext();
        log.info("User logged out successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
        return userMapper.toResponse(user);
    }

    private void handleFailedLogin(User user) {
        short currentAttempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0;
        short updatedAttempts = (short) (currentAttempts + 1);
        user.setFailedLoginAttempts(updatedAttempts);

        if (updatedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES));
            log.warn("User account id={} temporarily locked until={}", user.getId(), user.getLockedUntil());
        }

        userRepository.save(user);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
