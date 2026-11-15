package com.flyscanner.saved.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SavedFlightResponse(
        UUID id,
        String offerToken,
        String airline,
        String airlineCode,
        String code,
        String from,
        String to,
        String depart,
        String arrive,
        String duration,
        int durationMinutes,
        int stops,
        BigDecimal price,
        String currency,
        String fareType,
        Instant savedAt,
        Instant checkedAt,
        Instant offerExpiresAt
) {
}
