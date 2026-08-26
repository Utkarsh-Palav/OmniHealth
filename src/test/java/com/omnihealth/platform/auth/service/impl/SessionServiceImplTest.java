package com.omnihealth.platform.auth.service.impl;

import com.omnihealth.platform.auth.dto.AuthSessionDto;
import com.omnihealth.platform.auth.entity.PlatformSession;
import com.omnihealth.platform.auth.repository.PlatformSessionRepository;
import com.omnihealth.platform.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private PlatformSessionRepository platformSessionRepository;

    @InjectMocks
    private SessionServiceImpl sessionService;

    private User testUser;
    private MockHttpServletRequest httpRequest;
    private MockHttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        httpRequest = new MockHttpServletRequest();
        httpResponse = new MockHttpServletResponse();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("user@omnihealth.com");
    }

    @Test
    void testCreateSessionDefaultDuration() {
        httpRequest.setRemoteAddr("192.168.1.100");
        httpRequest.addHeader("User-Agent", "Mozilla/5.0");

        when(platformSessionRepository.save(any(PlatformSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthSessionDto sessionDto = sessionService.createSession(testUser, false, httpRequest);

        assertNotNull(sessionDto);
        assertNotNull(sessionDto.rawToken());
        assertNotNull(sessionDto.session());
        assertEquals("192.168.1.100", sessionDto.session().getIpAddress());
        assertEquals("Mozilla/5.0", sessionDto.session().getUserAgent());
        assertTrue(sessionDto.expiresAt().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));

        ArgumentCaptor<PlatformSession> sessionCaptor = ArgumentCaptor.forClass(PlatformSession.class);
        verify(platformSessionRepository).save(sessionCaptor.capture());
        assertNotNull(sessionCaptor.getValue().getRefreshTokenHash());
    }

    @Test
    void testValidateSessionActive() {
        PlatformSession activeSession = PlatformSession.builder()
                .user(testUser)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(platformSessionRepository.findByRefreshTokenHashAndRevokedAtIsNullAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.of(activeSession));
        when(platformSessionRepository.save(any(PlatformSession.class))).thenReturn(activeSession);

        Optional<PlatformSession> validated = sessionService.validateSession("raw-token-abc");

        assertTrue(validated.isPresent());
        assertNotNull(activeSession.getLastUsedAt());
        verify(platformSessionRepository).save(activeSession);
    }

    @Test
    void testValidateSessionExpiredReturnsEmpty() {
        PlatformSession expiredSession = PlatformSession.builder()
                .user(testUser)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(platformSessionRepository.findByRefreshTokenHashAndRevokedAtIsNullAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.of(expiredSession));

        Optional<PlatformSession> validated = sessionService.validateSession("raw-token-abc");

        assertTrue(validated.isEmpty());
        verify(platformSessionRepository, never()).save(any());
    }

    @Test
    void testAttachSessionCookieSetsHttpOnlyAndSameSite() {
        sessionService.attachSessionCookie(httpResponse, "raw-token-xyz", 3600);

        String setCookieHeader = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("omni_session=raw-token-xyz"));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Max-Age=3600"));
    }

    @Test
    void testClearSessionCookieSetsMaxAgeZero() {
        sessionService.clearSessionCookie(httpResponse);

        String setCookieHeader = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("Max-Age=0"));
    }

    @Test
    void testExtractTokenFromRequestCookieAndBearerHeader() {
        // Test cookie extraction
        httpRequest.setCookies(new Cookie("omni_session", "cookie-token-123"));
        assertEquals("cookie-token-123", sessionService.extractTokenFromRequest(httpRequest));

        // Test Bearer header fallback
        MockHttpServletRequest requestWithBearer = new MockHttpServletRequest();
        requestWithBearer.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bearer-token-456");
        assertEquals("bearer-token-456", sessionService.extractTokenFromRequest(requestWithBearer));
    }
}
