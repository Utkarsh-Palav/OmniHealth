package com.omnihealth.platform.auth.service.impl;

import com.omnihealth.common.enums.TokenType;
import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.common.exception.ConflictException;
import com.omnihealth.common.exception.UnauthorizedException;
import com.omnihealth.platform.auth.dto.AuthSessionDto;
import com.omnihealth.platform.auth.dto.request.LoginRequest;
import com.omnihealth.platform.auth.dto.response.LoginResponse;
import com.omnihealth.platform.auth.entity.PlatformSession;
import com.omnihealth.platform.auth.service.SessionService;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.entity.VerificationToken;
import com.omnihealth.platform.user.mapper.UserMapper;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private MockHttpServletRequest httpRequest;
    private MockHttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        httpRequest = new MockHttpServletRequest();
        httpResponse = new MockHttpServletResponse();

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("doctor@omnihealth.com");
        testUser.setPasswordHash("hashed_password");
        testUser.setFirstName("Doctor");
        testUser.setLastName("Who");
        testUser.setUserStatus(UserStatus.ACTIVE);
        testUser.setFailedLoginAttempts((short) 0);
    }

    @Test
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("doctor@omnihealth.com", "Password123!", false);

        when(userRepository.findByEmail("doctor@omnihealth.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_password")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        PlatformSession session = PlatformSession.builder()
                .user(testUser)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        AuthSessionDto sessionDto = new AuthSessionDto("raw-token-123", session, session.getExpiresAt());

        when(sessionService.createSession(eq(testUser), eq(false), eq(httpRequest))).thenReturn(sessionDto);
        when(userMapper.toResponse(testUser)).thenReturn(UserResponse.builder()
                .id(testUser.getId())
                .email(testUser.getEmail())
                .status(UserStatus.ACTIVE)
                .build());

        LoginResponse response = authService.login(request, httpRequest, httpResponse);

        assertNotNull(response);
        assertEquals(testUser.getEmail(), response.user().email());
        assertEquals("Bearer", response.tokenType());
        verify(sessionService).attachSessionCookie(eq(httpResponse), eq("raw-token-123"), anyLong());
    }

    @Test
    void testLoginInvalidPasswordIncrementsFailedAttempts() {
        LoginRequest request = new LoginRequest("doctor@omnihealth.com", "WrongPassword!", false);

        when(userRepository.findByEmail("doctor@omnihealth.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword!", "hashed_password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request, httpRequest, httpResponse));

        assertEquals((short) 1, testUser.getFailedLoginAttempts());
        verify(userRepository).save(testUser);
        verify(sessionService, never()).createSession(any(), anyBoolean(), any());
    }

    @Test
    void testLoginAccountLockoutAfter5FailedAttempts() {
        testUser.setFailedLoginAttempts((short) 4);
        LoginRequest request = new LoginRequest("doctor@omnihealth.com", "WrongPassword!", false);

        when(userRepository.findByEmail("doctor@omnihealth.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword!", "hashed_password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request, httpRequest, httpResponse));

        assertEquals((short) 5, testUser.getFailedLoginAttempts());
        assertNotNull(testUser.getLockedUntil());
        assertTrue(testUser.getLockedUntil().isAfter(Instant.now()));
        verify(userRepository).save(testUser);
    }

    @Test
    void testLoginLockedAccountThrowsUnauthorizedException() {
        testUser.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        LoginRequest request = new LoginRequest("doctor@omnihealth.com", "Password123!", false);

        when(userRepository.findByEmail("doctor@omnihealth.com")).thenReturn(Optional.of(testUser));

        assertThrows(UnauthorizedException.class, () -> authService.login(request, httpRequest, httpResponse));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testLoginUnverifiedEmailThrowsUnauthorizedException() {
        testUser.setUserStatus(UserStatus.PENDING_EMAIL_VERIFICATION);
        LoginRequest request = new LoginRequest("doctor@omnihealth.com", "Password123!", false);

        when(userRepository.findByEmail("doctor@omnihealth.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Password123!", "hashed_password")).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> authService.login(request, httpRequest, httpResponse));
    }

    @Test
    void testVerifyEmailAndAuthenticateSuccess() {
        String rawToken = "verify-token-xyz";
        VerificationToken token = new VerificationToken();
        token.setUser(testUser);
        token.setTokenType(TokenType.EMAIL_VERIFICATION);
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(verificationTokenRepository.findByTokenHashAndTokenTypeAndDeletedAtIsNull(anyString(), eq(TokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        PlatformSession session = PlatformSession.builder()
                .user(testUser)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        AuthSessionDto sessionDto = new AuthSessionDto("session-token-abc", session, session.getExpiresAt());

        when(sessionService.createSession(eq(testUser), eq(false), eq(httpRequest))).thenReturn(sessionDto);
        when(userMapper.toResponse(testUser)).thenReturn(UserResponse.builder()
                .id(testUser.getId())
                .email(testUser.getEmail())
                .status(UserStatus.ACTIVE)
                .build());

        LoginResponse response = authService.verifyEmailAndAuthenticate(rawToken, httpRequest, httpResponse);

        assertNotNull(response);
        assertNotNull(token.getConsumedAt());
        assertEquals(UserStatus.ACTIVE, testUser.getUserStatus());
        assertNotNull(testUser.getEmailVerifiedAt());
        verify(verificationTokenRepository).save(token);
        verify(userRepository).save(testUser);
        verify(sessionService).attachSessionCookie(eq(httpResponse), eq("session-token-abc"), anyLong());
    }

    @Test
    void testVerifyEmailAlreadyConsumedThrowsConflictException() {
        String rawToken = "consumed-token";
        VerificationToken token = new VerificationToken();
        token.setUser(testUser);
        token.setTokenType(TokenType.EMAIL_VERIFICATION);
        token.setConsumedAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(verificationTokenRepository.findByTokenHashAndTokenTypeAndDeletedAtIsNull(anyString(), eq(TokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        assertThrows(ConflictException.class, () -> authService.verifyEmailAndAuthenticate(rawToken, httpRequest, httpResponse));
    }

    @Test
    void testLogoutRevokesSessionAndClearsCookie() {
        when(sessionService.extractTokenFromRequest(httpRequest)).thenReturn("raw-session-token");

        authService.logout(httpRequest, httpResponse);

        verify(sessionService).revokeSession("raw-session-token");
        verify(sessionService).clearSessionCookie(httpResponse);
    }
}
