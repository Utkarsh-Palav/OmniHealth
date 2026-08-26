package com.omnihealth.platform.onboarding.controller;

import com.omnihealth.common.builder.ApiResponseBuilder;
import com.omnihealth.common.constants.ApiRoutes;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.platform.onboarding.dto.request.CompletePaymentRequest;
import com.omnihealth.platform.onboarding.dto.request.SignupRequest;
import com.omnihealth.platform.onboarding.dto.response.OnboardingSessionResponse;
import com.omnihealth.platform.onboarding.service.OnboardingService;
import com.omnihealth.platform.organization.dto.request.UpdateOrganizationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiRoutes.ONBOARDING)
@RequiredArgsConstructor
@Validated
@Tag(name = "Onboarding", description = "Self-service organization onboarding APIs")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final ApiResponseBuilder apiResponseBuilder;

    @PostMapping("/signup")
    @Operation(
            summary = "Sign up",
            description = "Creates the owner account and a draft organization, then opens "
                    + "an onboarding session. A verification email is sent to the owner."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response = onboardingService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        apiResponseBuilder.created(
                                response,
                                "Signup successful. Please verify your email to continue.",
                                httpRequest
                        )
                );
    }

    @PostMapping("/{id}/organization-profile")
    @Operation(
            summary = "Complete organization profile",
            description = "Fills in the remaining organization details after email verification."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> completeOrganizationProfile(
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody UpdateOrganizationRequest request,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response =
                onboardingService.completeOrganizationProfile(sessionId, request);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Organization profile completed successfully.",
                        httpRequest
                )
        );
    }

    @PostMapping("/{id}/provision-tenant")
    @Operation(
            summary = "Provision tenant",
            description = "Provisions the organization's tenant database (simulated)."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> provisionTenant(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response = onboardingService.provisionTenant(sessionId);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Tenant provisioning completed successfully.",
                        httpRequest
                )
        );
    }

    @PostMapping("/{id}/start-trial")
    @Operation(
            summary = "Start trial",
            description = "Starts the trial subscription on the default trial plan."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> startTrial(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response = onboardingService.startTrial(sessionId);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Trial started successfully.",
                        httpRequest
                )
        );
    }

    @PostMapping("/{id}/payment")
    @Operation(
            summary = "Complete payment",
            description = "Optionally records a payment for the subscription before activation."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> completePayment(
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody(required = false) CompletePaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response =
                onboardingService.completePayment(sessionId, request);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Payment recorded successfully.",
                        httpRequest
                )
        );
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate organization",
            description = "Activates the organization and completes onboarding."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> activate(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response = onboardingService.activate(sessionId);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Onboarding completed. Organization is now active.",
                        httpRequest
                )
        );
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get onboarding session",
            description = "Retrieves the current state of an onboarding session."
    )
    public ResponseEntity<ApiResponse<OnboardingSessionResponse>> getSession(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest
    ) {
        final OnboardingSessionResponse response = onboardingService.getSession(sessionId);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        response,
                        "Onboarding session retrieved successfully.",
                        httpRequest
                )
        );
    }
}
