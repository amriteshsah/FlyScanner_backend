package com.flyscanner.search.persistence;

import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightOfferResponse;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.FlightSearchResponse;
import com.flyscanner.search.api.dto.ProviderSearchStatus;
import com.flyscanner.search.domain.FlightJourney;
import com.flyscanner.search.domain.FlightSegment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists the search request, supplier status and synthetic/live offer snapshot.
 * Uses portable JDBC and the same Flyway schema for H2 and PostgreSQL.
 */
@Repository
public class JdbcFlightSearchResultRepository implements FlightSearchResultRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcFlightSearchResultRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(FlightSearchRequest request, FlightSearchResponse response) {
        UUID searchId = response.searchId();
        Instant createdAt = clock.instant();

        jdbcTemplate.update("""
                INSERT INTO search.flight_searches (
                    id, trip_type, cabin_class, currency, market, locale,
                    adult_count, infant_count, search_status, generated_at, disclaimer, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                searchId,
                request.tripType().name(),
                request.cabinClass().name(),
                request.currency(),
                request.market(),
                request.locale(),
                request.passengers().adults(),
                request.passengers().infants(),
                response.status().name(),
                Timestamp.from(response.generatedAt()),
                response.disclaimer(),
                Timestamp.from(createdAt)
        );

        saveLegs(searchId, request.legs());
        saveChildren(searchId, request.passengers().childrenAges());
        saveProviderStatuses(searchId, response.providerStatuses());
        saveOffers(response.offers(), searchId, createdAt);
    }

    private void saveLegs(UUID searchId, List<FlightLegRequest> legs) {
        for (int index = 0; index < legs.size(); index++) {
            FlightLegRequest leg = legs.get(index);
            jdbcTemplate.update("""
                    INSERT INTO search.flight_legs (
                        search_id, leg_number, origin, destination, departure_date
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    searchId,
                    index + 1,
                    leg.origin(),
                    leg.destination(),
                    Date.valueOf(leg.departureDate())
            );
        }
    }

    private void saveChildren(UUID searchId, List<Integer> childAges) {
        if (childAges == null) {
            return;
        }
        for (int index = 0; index < childAges.size(); index++) {
            jdbcTemplate.update("""
                    INSERT INTO search.search_children (search_id, child_number, age_years)
                    VALUES (?, ?, ?)
                    """, searchId, index + 1, childAges.get(index));
        }
    }

    private void saveProviderStatuses(UUID searchId, List<ProviderSearchStatus> statuses) {
        for (ProviderSearchStatus status : statuses) {
            jdbcTemplate.update("""
                    INSERT INTO search.provider_search_statuses (
                        search_id, provider_code, call_status, duration_ms, status_message
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    searchId,
                    status.providerCode(),
                    status.status().name(),
                    status.latencyMs(),
                    status.message()
            );
        }
    }

    private void saveOffers(List<FlightOfferResponse> offers, UUID searchId, Instant createdAt) {
        for (FlightOfferResponse offer : offers) {
            jdbcTemplate.update("""
                    INSERT INTO search.flight_offers (
                        offer_token, search_id, provider_code, price_amount, price_currency,
                        stop_count, total_duration_minutes, checked_at, expires_at,
                        affiliate_redirect_available, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    offer.offerToken(),
                    searchId,
                    offer.providerCode(),
                    offer.price().amount(),
                    offer.price().currency(),
                    offer.stops(),
                    offer.totalDurationMinutes(),
                    Timestamp.from(offer.checkedAt()),
                    Timestamp.from(offer.expiresAt()),
                    offer.affiliateRedirectAvailable(),
                    Timestamp.from(createdAt)
            );
            saveJourneysAndSegments(offer);
        }
    }

    private void saveJourneysAndSegments(FlightOfferResponse offer) {
        for (FlightJourney journey : offer.itinerary()) {
            jdbcTemplate.update("""
                    INSERT INTO search.flight_journeys (
                        offer_token, leg_number, origin, destination, departure_date,
                        duration_minutes, stop_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    offer.offerToken(),
                    journey.legNumber(),
                    journey.origin(),
                    journey.destination(),
                    Date.valueOf(journey.departureDate()),
                    journey.durationMinutes(),
                    journey.stopCount()
            );

            for (int index = 0; index < journey.segments().size(); index++) {
                FlightSegment segment = journey.segments().get(index);
                jdbcTemplate.update("""
                        INSERT INTO search.flight_segments (
                            offer_token, leg_number, segment_number, origin, destination,
                            carrier_code, flight_number, departure_at, arrival_at, duration_minutes
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        offer.offerToken(),
                        journey.legNumber(),
                        index + 1,
                        segment.origin(),
                        segment.destination(),
                        segment.carrierCode(),
                        segment.flightNumber(),
                        Timestamp.from(segment.departureAt()),
                        Timestamp.from(segment.arrivalAt()),
                        segment.durationMinutes()
                );
            }
        }
    }
}
