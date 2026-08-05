package com.omnihealth.platform.organization.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateOrganizationRequest(
        @Size(max = 255)
        String legalName,

        @Size(max = 255)
        String displayName,

        @Email
        @Size(max = 255)
        String email,

        @Pattern(
                regexp = "^[0-9]{10,15}$",
                message = "Phone number must contain 10 to 15 digits"
        )
        String phone,

        @Size(max = 255)
        String website,

        @Size(max = 500)
        String logoUrl,

        @Pattern(regexp = "^[A-Z]{2}$")
        String countryCode,

        @Size(max = 100)
        String timezone,

        @Pattern(regexp = "^[A-Z]{3}$")
        String currencyCode,

        Boolean demo
) {
}
