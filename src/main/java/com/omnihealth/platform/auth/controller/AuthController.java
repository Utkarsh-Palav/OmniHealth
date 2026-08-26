package com.omnihealth.platform.auth.controller;

import com.omnihealth.common.builder.ApiResponseBuilder;
import com.omnihealth.common.constants.ApiRoutes;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.common.security.PlatformUserPrincipal;
import com.omnihealth.platform.auth.dto.request.LoginRequest;
import com.omnihealth.platform.auth.dto.response.LoginResponse;
import com.omnihealth.platform.auth.service.AuthService;
import com.omnihealth.platform.user.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiRoutes.AUTH)
@RequiredArgsConstructor
@Validated
@Tag(name = "Authentication", description = "Platform Authentication & Session Management APIs")
public class AuthController {

    private final AuthService authService;
    private final ApiResponseBuilder apiResponseBuilder;

    @PostMapping("/login")
    @Operation(
            summary = "User Login",
            description = "Authenticates user credentials and establishes an HTTP-only secure session cookie."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        LoginResponse response = authService.login(request, httpRequest, httpResponse);

        return ResponseEntity.ok(
                apiResponseBuilder.success(response, "Login successful", httpRequest)
        );
    }

    @PostMapping("/verify-email")
    @Operation(
            summary = "Verify Email and Authenticate",
            description = "Verifies email token, activates user, and automatically establishes an authenticated session."
    )
    public ResponseEntity<ApiResponse<LoginResponse>> verifyEmail(
            @RequestParam("token") String token,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        LoginResponse response = authService.verifyEmailAndAuthenticate(token, httpRequest, httpResponse);

        return ResponseEntity.ok(
                apiResponseBuilder.success(response, "Email verified successfully. Session established.", httpRequest)
        );
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get Current Authenticated User",
            description = "Returns profile information of the currently authenticated platform user."
    )
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal PlatformUserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        UserResponse response = authService.getCurrentUser(principal.getUserId());

        return ResponseEntity.ok(
                apiResponseBuilder.success(response, "Authenticated user profile retrieved", httpRequest)
        );
    }

    @PostMapping("/logout")
    @Operation(
            summary = "User Logout",
            description = "Revokes the active session and clears the session cookie."
    )
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        authService.logout(httpRequest, httpResponse);

        return ResponseEntity.ok(
                apiResponseBuilder.success(null, "Logout successful", httpRequest)
        );
    }
}
