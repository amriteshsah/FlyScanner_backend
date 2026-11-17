package com.flyscanner.account.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountRecord(
        UUID id,
        String email,
        String fullName,
        String passwordHash,
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
