package com.omnihealth.platform.auth.service.impl;

import com.omnihealth.platform.auth.dto.AuthSessionDto;
import com.omnihealth.platform.auth.entity.PlatformSession;
import com.omnihealth.platform.auth.repository.PlatformSessionRepository;
import com.omnihealth.platform.auth.service.SessionService;
import com.omnihealth.platform.user.entity.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SessionServiceImpl implements SessionService {

    public static final String COOKIE_NAME = "omni_session";
    public static final long DEFAULT_SESSION_DURATION_DAYS = 7;
    public static final long REMEMBER_ME_SESSION_DURATION_DAYS = 30;

    private final PlatformSessionRepository platformSessionRepository;

    @Value("${app.security.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${app.security.cookie.same-site:Lax}")
    private String sameSite;

    @Override
    public AuthSessionDto createSession(User user, boolean rememberMe, HttpServletRequest request) {
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        long durationDays = rememberMe ? REMEMBER_ME_SESSION_DURATION_DAYS : DEFAULT_SESSION_DURATION_DAYS;
        Instant now = Instant.now();
        Instant expiresAt = now.plus(durationDays, ChronoUnit.DAYS);

        String ipAddress = extractClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String deviceId = request != null ? request.getHeader("X-Device-Id") : null;

        PlatformSession session = PlatformSession.builder()
                .user(user)
                .refreshTokenHash(tokenHash)
                .expiresAt(expiresAt)
                .lastUsedAt(now)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .build();

        PlatformSession savedSession = platformSessionRepository.save(session);
        log.info("Created new authenticated session id={} for user id={}", savedSession.getId(), user.getId());

        return new AuthSessionDto(rawToken, savedSession, expiresAt);
    }

    @Override
    public Optional<PlatformSession> validateSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(rawToken);
        // Fetch the user in the same query: the caller (SessionAuthenticationFilter)
        // reads session.getUser() after this @Transactional method returns, i.e. with
        // the persistence context already closed. A lazy user proxy would throw
        // LazyInitializationException there, so we initialize it eagerly here.
        Optional<PlatformSession> sessionOpt = platformSessionRepository
                .findActiveByRefreshTokenHashWithUser(tokenHash);

        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        PlatformSession session = sessionOpt.get();
        if (!session.isActive()) {
            return Optional.empty();
        }

        session.setLastUsedAt(Instant.now());
        platformSessionRepository.save(session);

        return Optional.of(session);
    }

    @Override
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawToken);
        platformSessionRepository
                .findByRefreshTokenHashAndRevokedAtIsNullAndDeletedAtIsNull(tokenHash)
                .ifPresent(session -> {
                    session.setRevokedAt(Instant.now());
                    platformSessionRepository.save(session);
                    log.info("Revoked session id={}", session.getId());
                });
    }

    @Override
    public void revokeAllUserSessions(UUID userId) {
        if (userId == null) {
            return;
        }
        platformSessionRepository.revokeAllByUserId(userId, Instant.now());
        log.info("Revoked all sessions for user id={}", userId);
    }

    @Override
    public void attachSessionCookie(HttpServletResponse response, String rawToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public String extractTokenFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. Check cookies first
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 2. Check Authorization Header as fallback
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return null;
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

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
