package com.flyscanner.account.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Authenticated account identity and optional traveler profile placed in the Spring Security context. */
public record AccountPrincipal(
        UUID userId,
        String email,
        String fullName,
        String role,
        Instant createdAt,
        String phoneNumber,
        LocalDate dateOfBirth,
        String nationality,
        String homeAirport,
        String preferredCurrency
) {
}
