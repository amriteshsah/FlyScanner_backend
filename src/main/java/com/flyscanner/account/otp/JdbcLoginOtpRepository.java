package com.flyscanner.account.otp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcLoginOtpRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLoginOtpRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Locks a durable per-email row before inspecting or creating a challenge. */
    public void lockEmail(String email) {
        jdbcTemplate.update("""
                MERGE INTO account.email_login_otp_locks target
                USING (SELECT CAST(? AS VARCHAR(320)) AS email) source
                ON target.email = source.email
                WHEN NOT MATCHED THEN INSERT (email) VALUES (source.email)
                """, email);
        jdbcTemplate.queryForObject(
                "SELECT email FROM account.email_login_otp_locks WHERE email = ? FOR UPDATE",
                String.class,
                email
        );
    }

    public Optional<LoginOtpChallenge> findByEmailForUpdate(String email) {
        return jdbcTemplate.query("""
                SELECT email, otp_hash, sent_at, expires_at, failed_attempts, consumed_at
                FROM account.email_login_otps
                WHERE email = ?
                FOR UPDATE
                """, (rs, rowNum) -> new LoginOtpChallenge(
                rs.getString("email"),
                rs.getString("otp_hash"),
                rs.getTimestamp("sent_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getInt("failed_attempts"),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()
        ), email).stream().findFirst();
    }

    public void save(String email, String otpHash, Instant sentAt, Instant expiresAt) {
        int updated = jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET otp_hash = ?, sent_at = ?, expires_at = ?, failed_attempts = 0, consumed_at = NULL
                WHERE email = ?
                """, otpHash, Timestamp.from(sentAt), Timestamp.from(expiresAt), email);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO account.email_login_otps (
                        email, otp_hash, sent_at, expires_at, failed_attempts, consumed_at
                    ) VALUES (?, ?, ?, ?, 0, NULL)
                    """, email, otpHash, Timestamp.from(sentAt), Timestamp.from(expiresAt));
        }
    }

    public void recordFailedAttempt(String email, Instant attemptedAt) {
        jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET failed_attempts = failed_attempts + 1,
                    consumed_at = CASE WHEN failed_attempts + 1 >= 5 THEN ? ELSE consumed_at END
                WHERE email = ? AND consumed_at IS NULL AND failed_attempts < 5
                """, Timestamp.from(attemptedAt), email);
    }

    public void consume(String email, Instant consumedAt) {
        jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET consumed_at = ?
                WHERE email = ? AND consumed_at IS NULL
                """, Timestamp.from(consumedAt), email);
    }
}
