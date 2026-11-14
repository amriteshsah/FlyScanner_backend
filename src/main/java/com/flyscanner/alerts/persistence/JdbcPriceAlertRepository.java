package com.flyscanner.alerts.persistence;

import com.flyscanner.alerts.api.PriceAlertResponse;
import com.flyscanner.search.domain.TripType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPriceAlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPriceAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PriceAlertResponse insert(
            UUID id, UUID userId, String origin, String destination, TripType tripType,
            java.time.LocalDate departureDate, java.time.LocalDate returnDate,
            java.math.BigDecimal targetPrice, String currency, String market, Instant createdAt, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO alerts.price_alerts (
                    id, user_id, origin, destination, trip_type, departure_date,
                    return_date, target_price, currency, market, is_active, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id, userId, origin, destination, tripType.name(),
                Date.valueOf(departureDate),
                returnDate == null ? null : Date.valueOf(returnDate),
                targetPrice, currency, market, Timestamp.from(createdAt),
                expiresAt == null ? null : Timestamp.from(expiresAt)
        );
        return findByIdAndUser(id, userId).orElseThrow();
    }

    public List<PriceAlertResponse> findAllByUser(UUID userId) {
        return jdbcTemplate.query("""
                SELECT id, origin, destination, trip_type, departure_date, return_date,
                       target_price, currency, market, is_active, created_at, expires_at
                FROM alerts.price_alerts
                WHERE user_id = ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> new PriceAlertResponse(
                rs.getObject("id", UUID.class),
                rs.getString("origin"),
                rs.getString("destination"),
                TripType.valueOf(rs.getString("trip_type")),
                rs.getDate("departure_date").toLocalDate(),
                rs.getDate("return_date") == null ? null : rs.getDate("return_date").toLocalDate(),
                rs.getBigDecimal("target_price"),
                rs.getString("currency"),
                rs.getString("market"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()
        ), userId);
    }

    public Optional<PriceAlertResponse> findByIdAndUser(UUID id, UUID userId) {
        return jdbcTemplate.query("""
                SELECT id, origin, destination, trip_type, departure_date, return_date,
                       target_price, currency, is_active, created_at, expires_at
                FROM alerts.price_alerts
                WHERE id = ? AND user_id = ?
                """, (rs, rowNum) -> new PriceAlertResponse(
                rs.getObject("id", UUID.class),
                rs.getString("origin"),
                rs.getString("destination"),
                TripType.valueOf(rs.getString("trip_type")),
                rs.getDate("departure_date").toLocalDate(),
                rs.getDate("return_date") == null ? null : rs.getDate("return_date").toLocalDate(),
                rs.getBigDecimal("target_price"),
                rs.getString("currency"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()
        ), id, userId).stream().findFirst();
    }

    public boolean deactivate(UUID id, UUID userId) {
        int updated = jdbcTemplate.update("""
                UPDATE alerts.price_alerts
                SET is_active = FALSE
                WHERE id = ? AND user_id = ? AND is_active = TRUE
                """, id, userId);
        return updated > 0;
    }
}
