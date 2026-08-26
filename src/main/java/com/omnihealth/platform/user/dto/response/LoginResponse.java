package com.omnihealth.platform.user.dto.response;

import lombok.Builder;

import java.time.Instant;

@Builder
public record LoginResponse(
        UserResponse user,
        Instant expiresAt
) {
}
