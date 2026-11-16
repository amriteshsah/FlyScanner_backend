package com.flyscanner.account.otp;

import java.time.Instant;
import java.util.UUID;

public record LoginOtpChallenge(
        UUID userId,
        String otpHash,
        Instant sentAt,
        Instant expiresAt,
        int failedAttempts,
        Instant consumedAt
) {
}
