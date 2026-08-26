package com.omnihealth.platform.auth.dto.response;

import com.omnihealth.platform.user.dto.response.UserResponse;
import lombok.Builder;

import java.time.Instant;

@Builder
public record LoginResponse(
        UserResponse user,
        Instant expiresAt,
        String tokenType
) {
}
