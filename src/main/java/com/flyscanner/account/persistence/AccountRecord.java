package com.flyscanner.account.persistence;

import java.time.Instant;
import java.util.UUID;

public record AccountRecord(
        UUID id,
        String email,
        String fullName,
        String passwordHash,
        String role,
        Instant createdAt
) {
}
