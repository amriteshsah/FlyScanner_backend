package com.flyscanner.account.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID id, String email, String fullName, String passwordHash, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO account.users (
                    id, external_subject, email, full_name, user_status,
                    created_at, updated_at, password_hash
                ) VALUES (?, NULL, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                id, email, fullName, Timestamp.from(now), Timestamp.from(now), passwordHash
        );
    }

    public Optional<AccountRecord> findByEmail(String email) {
        return jdbcTemplate.query("""
                SELECT id, email, full_name, password_hash
                FROM account.users
                WHERE email = ? AND user_status = 'ACTIVE'
                """,
                (rs, rowNum) -> new AccountRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("password_hash")
                ),
                email
        ).stream().findFirst();
    }

    public Optional<AccountRecord> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, email, full_name, password_hash
                FROM account.users
                WHERE id = ? AND user_status = 'ACTIVE'
                """,
                (rs, rowNum) -> new AccountRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("password_hash")
                ),
                id
        ).stream().findFirst();
    }
}
