package com.omnihealth.platform.organization.dto.response;

import com.omnihealth.platform.organization.entity.OrganizationStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record OrganizationResponse (

        UUID id,

        String organizationCode,

        String legalName,

        String displayName,

        String email,

        String phone,

        String website,

        String logoUrl,

        String countryCode,

        String timezone,

        String currencyCode,

        OrganizationStatus status,

        boolean demo,

        Instant createdAt,

        Instant updatedAt

) {}
