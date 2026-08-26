package com.omnihealth.platform.onboarding.dto.request;

import com.omnihealth.platform.organization.entity.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Minimal self-service signup payload.
 *
 * <p>
 * Collects only what is needed to create the owner account and a draft
 * organization: the owner's name, email and password, plus the company's
 * display name and type. Every other organization profile field is captured
 * later, during the complete-profile step.
 * </p>
 */
public record SignupRequest(

        // ---- Owner account ----

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        // ---- Company ----

        @NotBlank
        @Size(max = 225)
        String displayName,

        @NotNull
        OrganizationType organizationType
) {
}
