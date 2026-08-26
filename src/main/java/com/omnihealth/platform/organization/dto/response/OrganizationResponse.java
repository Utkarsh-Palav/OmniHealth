package com.omnihealth.platform.organization.dto.response;

import com.omnihealth.platform.organization.entity.OrganizationStatus;
import com.omnihealth.platform.organization.entity.OrganizationType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record OrganizationResponse(

        UUID id,

        String organizationCode,

        String legalName,

        String displayName,

        OrganizationType organizationType,

        String registrationNumber,

        String taxIdentificationNumber,

        String gstNumber,

        String panNumber,

        String officialEmail,

        String officialPhoneCountryCode,

        String officialPhoneNumber,

        String websiteUrl,

        String registeredAddressLine1,

        String registeredAddressLine2,

        String registeredCity,

        String registeredState,

        String registeredPostalCode,

        String registeredCountryCode,

        String timezone,

        String currencyCode,

        String locale,

        OrganizationStatus status,

        Instant activatedAt,

        Instant suspendedAt,

        Instant terminatedAt,

        Instant createdAt,

        Instant updatedAt

) {}