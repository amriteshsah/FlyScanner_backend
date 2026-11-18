package com.flyscanner.account.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Authenticated identity and optional traveler profile placed in the security context. */
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
        String preferredCurrency,
        boolean profileCompleted
) {
}
