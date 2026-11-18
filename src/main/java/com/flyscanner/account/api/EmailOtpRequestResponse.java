package com.flyscanner.account.api;

import java.time.Instant;

public record EmailOtpRequestResponse(
        String message,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
