package com.flyscanner.account.otp;

import java.time.Instant;

public record LoginOtpChallenge(
        String email,
        String otpHash,
        Instant sentAt,
        Instant expiresAt,
        int failedAttempts,
        Instant consumedAt
) {
}
