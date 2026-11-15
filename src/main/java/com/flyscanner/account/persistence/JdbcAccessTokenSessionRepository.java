package com.flyscanner.account.persistence;

import com.flyscanner.account.domain.AccountPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccessTokenSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccessTokenSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO account.access_token_sessions (
                    token_hash, user_id, created_at, expires_at, revoked_at
                ) VALUES (?, ?, ?, ?, NULL)
                """,
                tokenHash, userId, Timestamp.from(createdAt), Timestamp.from(expiresAt)
        );
    }

    public Optional<AccountPrincipal> findActivePrincipal(String tokenHash, Instant now) {
        return jdbcTemplate.query("""
                SELECT u.id, u.email, u.full_name, u.role, u.created_at,
                       u.phone_number, u.date_of_birth, u.nationality,
                       u.home_airport, u.preferred_currency
                FROM account.access_token_sessions s
                JOIN account.users u ON u.id = s.user_id
                WHERE s.token_hash = ?
                  AND s.revoked_at IS NULL
                  AND s.expires_at > ?
                  AND u.user_status = 'ACTIVE'
                """,
                (rs, rowNum) -> {
                    java.sql.Date dateOfBirth = rs.getDate("date_of_birth");
                    return new AccountPrincipal(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getString("phone_number"),
                            dateOfBirth == null ? null : dateOfBirth.toLocalDate(),
                            rs.getString("nationality"),
                            rs.getString("home_airport"),
                            rs.getString("preferred_currency")
                    );
                },
                tokenHash, Timestamp.from(now)
        ).stream().findFirst();
    }

    public void revoke(String tokenHash, Instant now) {
        jdbcTemplate.update("""
                UPDATE account.access_token_sessions
                SET revoked_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                """,
                Timestamp.from(now), tokenHash
        );
    }
}
