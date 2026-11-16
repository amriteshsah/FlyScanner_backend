package com.flyscanner.account.otp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLoginOtpRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLoginOtpRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Locks the account row to serialize initial OTP requests as well as resends. */
    public void lockAccount(UUID userId) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM account.users WHERE id = ? FOR UPDATE",
                UUID.class,
                userId
        );
    }

    public Optional<LoginOtpChallenge> findByUserIdForUpdate(UUID userId) {
        return jdbcTemplate.query("""
                SELECT user_id, otp_hash, sent_at, expires_at, failed_attempts, consumed_at
                FROM account.email_login_otps
                WHERE user_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new LoginOtpChallenge(
                rs.getObject("user_id", UUID.class),
                rs.getString("otp_hash"),
                rs.getTimestamp("sent_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getInt("failed_attempts"),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()
        ), userId).stream().findFirst();
    }

    public void save(UUID userId, String otpHash, Instant sentAt, Instant expiresAt) {
        int updated = jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET otp_hash = ?, sent_at = ?, expires_at = ?, failed_attempts = 0, consumed_at = NULL
                WHERE user_id = ?
                """, otpHash, Timestamp.from(sentAt), Timestamp.from(expiresAt), userId);

        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO account.email_login_otps (
                        user_id, otp_hash, sent_at, expires_at, failed_attempts, consumed_at
                    ) VALUES (?, ?, ?, ?, 0, NULL)
                    """, userId, otpHash, Timestamp.from(sentAt), Timestamp.from(expiresAt));
        }
    }

    public void recordFailedAttempt(UUID userId, Instant attemptedAt) {
        jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET failed_attempts = failed_attempts + 1,
                    consumed_at = CASE WHEN failed_attempts + 1 >= 5 THEN ? ELSE consumed_at END
                WHERE user_id = ? AND consumed_at IS NULL AND failed_attempts < 5
                """, Timestamp.from(attemptedAt), userId);
    }

    public void consume(UUID userId, Instant consumedAt) {
        jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET consumed_at = ?
                WHERE user_id = ? AND consumed_at IS NULL
                """, Timestamp.from(consumedAt), userId);
    }
}
