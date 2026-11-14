package com.flyscanner.account.domain;

import java.time.Instant;
import java.util.UUID;

/** Authenticated account identity placed in the Spring Security context. */
public record AccountPrincipal(
        UUID userId,
        String email,
        String fullName,
        String role,
        Instant createdAt
) {
}
