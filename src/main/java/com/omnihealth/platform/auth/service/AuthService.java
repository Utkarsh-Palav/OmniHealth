package com.omnihealth.platform.auth.service;

import com.omnihealth.platform.auth.dto.request.LoginRequest;
import com.omnihealth.platform.auth.dto.response.LoginResponse;
import com.omnihealth.platform.user.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

public interface AuthService {

    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    LoginResponse verifyEmailAndAuthenticate(String rawToken, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    UserResponse getCurrentUser(UUID userId);
}
