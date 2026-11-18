package com.flyscanner.alerts.api;

import com.flyscanner.search.domain.TripType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceAlertResponse(
        UUID id,
        String origin,
        String destination,
        TripType tripType,
        LocalDate departureDate,
        LocalDate returnDate,
        BigDecimal targetPrice,
        String currency,
        String market,
        boolean active,
        Instant createdAt,
        Instant expiresAt
) {
}
