package com.flyscanner.saved.persistence;

import com.flyscanner.saved.api.SavedFlightResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcSavedFlightRepository {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;

    public JdbcSavedFlightRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SavedFlightResponse> save(UUID userId, String offerToken, Instant savedAt) {
        Optional<SavedFlightResponse> existing = findByUserAndOffer(userId, offerToken);
        if (existing.isPresent()) {
            return existing;
        }

        Integer offerCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM search.flight_offers offer
                JOIN search.flight_journeys journey
                  ON journey.offer_token = offer.offer_token AND journey.leg_number = 1
                JOIN search.flight_segments segment
                  ON segment.offer_token = offer.offer_token AND segment.leg_number = 1
                WHERE offer.offer_token = ?
                """, Integer.class, offerToken);
        if (offerCount == null || offerCount == 0) {
            return Optional.empty();
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO account.saved_flights (id, user_id, offer_token, saved_at)
                VALUES (?, ?, ?, ?)
                """, id, userId, offerToken, Timestamp.from(savedAt));

        return findByIdAndUser(id, userId);
    }

    public List<SavedFlightResponse> findAllByUser(UUID userId) {
        return jdbcTemplate.query(savedFlightSelect() + """
                WHERE saved.user_id = ?
                ORDER BY saved.saved_at DESC
                """, (rs, rowNum) -> mapRow(
                rs.getObject("id", UUID.class),
                rs.getString("offer_token"),
                rs.getString("carrier_code"),
                rs.getString("flight_number"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getTimestamp("departure_at"),
                rs.getTimestamp("arrival_at"),
                rs.getInt("duration_minutes"),
                rs.getInt("stop_count"),
                rs.getBigDecimal("price_amount"),
                rs.getString("price_currency"),
                rs.getString("trip_type"),
                rs.getTimestamp("saved_at"),
                rs.getTimestamp("checked_at"),
                rs.getTimestamp("expires_at")
        ), userId);
    }

    public Optional<SavedFlightResponse> findByUserAndOffer(UUID userId, String offerToken) {
        return jdbcTemplate.query(savedFlightSelect() + """
                WHERE saved.user_id = ? AND saved.offer_token = ?
                """, (rs, rowNum) -> mapRow(
                rs.getObject("id", UUID.class),
                rs.getString("offer_token"),
                rs.getString("carrier_code"),
                rs.getString("flight_number"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getTimestamp("departure_at"),
                rs.getTimestamp("arrival_at"),
                rs.getInt("duration_minutes"),
                rs.getInt("stop_count"),
                rs.getBigDecimal("price_amount"),
                rs.getString("price_currency"),
                rs.getString("trip_type"),
                rs.getTimestamp("saved_at"),
                rs.getTimestamp("checked_at"),
                rs.getTimestamp("expires_at")
        ), userId, offerToken).stream().findFirst();
    }

    public Optional<SavedFlightResponse> findByIdAndUser(UUID id, UUID userId) {
        return jdbcTemplate.query(savedFlightSelect() + """
                WHERE saved.id = ? AND saved.user_id = ?
                """, (rs, rowNum) -> mapRow(
                rs.getObject("id", UUID.class),
                rs.getString("offer_token"),
                rs.getString("carrier_code"),
                rs.getString("flight_number"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getTimestamp("departure_at"),
                rs.getTimestamp("arrival_at"),
                rs.getInt("duration_minutes"),
                rs.getInt("stop_count"),
                rs.getBigDecimal("price_amount"),
                rs.getString("price_currency"),
                rs.getString("trip_type"),
                rs.getTimestamp("saved_at"),
                rs.getTimestamp("checked_at"),
                rs.getTimestamp("expires_at")
        ), id, userId).stream().findFirst();
    }

    public boolean delete(UUID id, UUID userId) {
        return jdbcTemplate.update("""
                DELETE FROM account.saved_flights
                WHERE id = ? AND user_id = ?
                """, id, userId) > 0;
    }

    private String savedFlightSelect() {
        return """
                SELECT saved.id, saved.offer_token, saved.saved_at,
                       offer.price_amount, offer.price_currency, offer.checked_at, offer.expires_at,
                       journey.origin, journey.destination, journey.duration_minutes, journey.stop_count,
                       first_segment.carrier_code, first_segment.flight_number,
                       first_segment.departure_at, last_segment.arrival_at, search.trip_type
                FROM account.saved_flights saved
                JOIN search.flight_offers offer ON offer.offer_token = saved.offer_token
                JOIN search.flight_searches search ON search.id = offer.search_id
                JOIN search.flight_journeys journey
                  ON journey.offer_token = offer.offer_token AND journey.leg_number = 1
                JOIN search.flight_segments first_segment
                  ON first_segment.offer_token = offer.offer_token
                 AND first_segment.leg_number = 1 AND first_segment.segment_number = 1
                JOIN search.flight_segments last_segment
                  ON last_segment.offer_token = offer.offer_token
                 AND last_segment.leg_number = 1
                 AND last_segment.segment_number = (
                     SELECT MAX(segment_candidate.segment_number)
                     FROM search.flight_segments segment_candidate
                     WHERE segment_candidate.offer_token = offer.offer_token
                       AND segment_candidate.leg_number = 1
                 )
                """;
    }

    private SavedFlightResponse mapRow(
            UUID id,
            String offerToken,
            String carrierCode,
            String flightNumber,
            String origin,
            String destination,
            Timestamp departureAt,
            Timestamp arrivalAt,
            int durationMinutes,
            int stops,
            java.math.BigDecimal price,
            String currency,
            String tripType,
            Timestamp savedAt,
            Timestamp checkedAt,
            Timestamp expiresAt) {
        Instant departure = departureAt.toInstant();
        Instant arrival = arrivalAt.toInstant();
        Instant saved = savedAt.toInstant();
        Instant checked = checkedAt.toInstant();
        Instant expiry = expiresAt == null ? null : expiresAt.toInstant();
        return new SavedFlightResponse(
                id,
                offerToken,
                "Carrier " + carrierCode,
                carrierCode,
                flightNumber,
                origin,
                destination,
                TIME_FORMAT.format(departure),
                TIME_FORMAT.format(arrival),
                formatDuration(durationMinutes),
                durationMinutes,
                stops,
                price,
                currency,
                tripType,
                saved,
                checked,
                expiry
        );
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int remainder = minutes % 60;
        return hours > 0 ? hours + "h " + remainder + "m" : remainder + "m";
    }
}
