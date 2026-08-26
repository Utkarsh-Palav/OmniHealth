package com.omnihealth.platform.organization.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateOrganizationRequest(

        @Size(max = 225)
        String legalName,

        @Size(max = 225)
        String displayName,

        @Email
        @Size(max = 225)
        String officialEmail,

        @Size(max = 5)
        String officialPhoneCountryCode,

        @Pattern(
                regexp = "^[0-9]{7,20}$",
                message = "Official phone number must contain 7 to 20 digits"
        )
        String officialPhoneNumber,

        @Size(max = 100)
        String registrationNumber,

        @Size(max = 100)
        String taxIdentificationNumber,

        @Pattern(
                regexp = "^[0-9]{2}[A-Z0-9]{13}$",
                message = "GST number must be a valid 15-character GST format"
        )
        String gstNumber,

        @Pattern(
                regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$",
                message = "PAN number must be a valid 10-character PAN format"
        )
        String panNumber,

        @Size(max = 500)
        String websiteUrl,

        @Size(max = 255)
        String registeredAddressLine1,

        @Size(max = 255)
        String registeredAddressLine2,

        @Size(max = 100)
        String registeredCity,

        @Size(max = 100)
        String registeredState,

        @Size(max = 20)
        String registeredPostalCode,

        @Pattern(
                regexp = "^[A-Z]{2}$",
                message = "Registered country code must contain exactly 2 uppercase letters"
        )
        String registeredCountryCode,

        @Size(max = 100)
        String timezone,

        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency code must contain exactly 3 uppercase letters"
        )
        String currencyCode,

        @Size(max = 20)
        String locale
) {
}