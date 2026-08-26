package com.omnihealth.platform.auth.service;

import com.omnihealth.platform.auth.dto.AuthSessionDto;
import com.omnihealth.platform.auth.entity.PlatformSession;
import com.omnihealth.platform.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;
import java.util.UUID;

public interface SessionService {

    AuthSessionDto createSession(User user, boolean rememberMe, HttpServletRequest request);

    Optional<PlatformSession> validateSession(String rawToken);

    void revokeSession(String rawToken);

    void revokeAllUserSessions(UUID userId);

    void attachSessionCookie(HttpServletResponse response, String rawToken, long maxAgeSeconds);

    void clearSessionCookie(HttpServletResponse response);

    String extractTokenFromRequest(HttpServletRequest request);
}
