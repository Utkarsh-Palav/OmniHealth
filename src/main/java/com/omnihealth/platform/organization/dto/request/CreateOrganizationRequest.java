package com.omnihealth.platform.organization.dto.request;

import com.omnihealth.platform.organization.entity.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateOrganizationRequest(

        @NotBlank
        @Size(max = 50)
        String organizationCode,

        @NotBlank
        @Size(max = 225)
        String legalName,

        @NotBlank
        @Size(max = 225)
        String displayName,

        @NotNull
        OrganizationType organizationType,

        @NotBlank
        @Email
        @Size(max = 225)
        String officialEmail,

        @Size(max = 5)
        String officialPhoneCountryCode,

        @Pattern(regexp = "^[0-9]{7,20}$")
        String officialPhoneNumber,

        @Size(max = 100)
        String registrationNumber,

        @Size(max = 100)
        String taxIdentificationNumber,

        @Pattern(regexp = "^[0-9]{2}[A-Z0-9]{13}$")
        String gstNumber,

        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$")
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

        @Pattern(regexp = "^[A-Z]{2}$")
        String registeredCountryCode,

        @NotBlank
        @Size(max = 100)
        String timezone,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currencyCode,

        @NotBlank
        @Size(max = 20)
        String locale
) {}