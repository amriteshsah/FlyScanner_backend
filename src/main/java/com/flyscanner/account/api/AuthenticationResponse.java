package com.flyscanner.account.api;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationResponse(
        UUID userId,
        String email,
        String fullName,
        String accessToken,
        Instant expiresAt,
        boolean isNewUser,
        boolean profileCompleted
) {
}
