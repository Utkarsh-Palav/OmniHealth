package com.omnihealth.platform.user.dto.response;

import com.omnihealth.common.enums.UserStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserResponse(

        UUID id,

        String email,

        String firstName,

        String middleName,

        String lastName,

        String preferredName,

        String phoneCountryCode,

        String phoneNumber,

        String profileImageKey,

        UserStatus status,

        Instant emailVerifiedAt,

        Instant lockedUntil,

        Instant lastLoginAt,

        Instant createdAt,

        Instant updatedAt

) {}