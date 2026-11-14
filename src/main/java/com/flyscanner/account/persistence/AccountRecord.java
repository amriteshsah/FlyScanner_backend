package com.flyscanner.account.persistence;

import java.util.UUID;

public record AccountRecord(UUID id, String email, String fullName, String passwordHash) {
}
