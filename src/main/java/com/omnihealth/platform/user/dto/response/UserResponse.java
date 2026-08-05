package com.omnihealth.platform.user.dto.response;


import com.omnihealth.common.enums.AuthProvider;
import com.omnihealth.common.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(

        UUID id,

        String firstName,

        String lastName,

        String email,

        String phone,

        String avatarUrl,

        AuthProvider authProvider,

        boolean emailVerified,

        boolean phoneVerified,

        Instant lastLoginAt,

        UserStatus status,

        Instant createdAt,

        Instant updatedAt

) {}