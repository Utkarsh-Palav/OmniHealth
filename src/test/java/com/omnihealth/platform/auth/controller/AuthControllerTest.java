package com.omnihealth.platform.auth.controller;

import com.omnihealth.common.builder.ApiResponseBuilder;
import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.platform.auth.dto.request.LoginRequest;
import com.omnihealth.platform.auth.dto.response.LoginResponse;
import com.omnihealth.platform.auth.service.AuthService;
import com.omnihealth.platform.user.dto.response.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Spy
    private ApiResponseBuilder apiResponseBuilder;

    @InjectMocks
    private AuthController authController;

    private MockHttpServletRequest httpRequest;
    private MockHttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        httpRequest = new MockHttpServletRequest();
        httpResponse = new MockHttpServletResponse();
    }

    @Test
    void testLoginControllerReturnsSuccess() {
        LoginRequest request = new LoginRequest("doctor@omnihealth.com", "Password123!", false);
        LoginResponse serviceResponse = LoginResponse.builder()
                .user(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .email("doctor@omnihealth.com")
                        .status(UserStatus.ACTIVE)
                        .build())
                .expiresAt(Instant.now().plusSeconds(3600))
                .tokenType("Bearer")
                .build();

        when(authService.login(eq(request), any(), any())).thenReturn(serviceResponse);

        ResponseEntity<?> response = authController.login(request, httpRequest, httpResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testVerifyEmailControllerReturnsSuccess() {
        LoginResponse serviceResponse = LoginResponse.builder()
                .user(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .email("doctor@omnihealth.com")
                        .status(UserStatus.ACTIVE)
                        .build())
                .expiresAt(Instant.now().plusSeconds(3600))
                .tokenType("Bearer")
                .build();

        when(authService.verifyEmailAndAuthenticate(eq("token-123"), any(), any())).thenReturn(serviceResponse);

        ResponseEntity<?> response = authController.verifyEmail("token-123", httpRequest, httpResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testLogoutControllerCallsAuthService() {
        ResponseEntity<?> response = authController.logout(httpRequest, httpResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).logout(httpRequest, httpResponse);
    }
}
