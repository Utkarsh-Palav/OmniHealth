package com.omnihealth.platform.auth.dto;

import com.omnihealth.platform.auth.entity.PlatformSession;

import java.time.Instant;

public record AuthSessionDto(
        String rawToken,
        PlatformSession session,
        Instant expiresAt
) {
}
