package com.omnihealth.platform.organization.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateOrganizationRequest (
    @NotBlank
    @Size(max = 50)
    String organizationCode,

    @NotBlank
    @Size(max = 225)
    String legalName,

    @NotBlank
    @Size(max = 225)
    String displayName,

    @NotBlank
    @Email
    @Size(max = 225)
    String email,

    @Pattern(regexp = "^[+]?[0-9]{7,20}$")
    String phone,

    @Size(max = 255)
    String website,

    @Size(max = 500)
    String logoUrl,

    @NotBlank
    @Pattern(regexp = "^[A-Z]{2}$")
    String countryCode,

    @NotBlank
    @Size(max = 100)
    String timezone,

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    String currencyCode,

    Boolean demo
) {}
