package com.flyscanner.search.persistence;

import com.flyscanner.search.api.dto.FlightJourney;
import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightOfferResponse;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.FlightSearchResponse;
import com.flyscanner.search.api.dto.ProviderSearchStatus;
import com.flyscanner.search.domain.FlightSegment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * JDBC persistence keeps the first release lightweight and works with both H2 and PostgreSQL.
 */
@Repository
public class JdbcFlightSearchResultRepository implements FlightSearchResultRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFlightSearchResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(FlightSearchRequest request, FlightSearchResponse response) {
        Instant createdAt = Instant.now();

        jdbcTemplate.update("""
                INSERT INTO search.flight_searches (
                    id, trip_type, cabin_class, currency, market, locale,
                    adult_count, infant_count, search_status, generated_at, disclaimer, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                response.searchId(),
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

        saveLegs(response.searchId().toString(), request.legs());
        saveChildren(response.searchId().toString(), request.passengers().childrenAges());
        saveProviderStatuses(response.searchId().toString(), response.providerStatuses());
        saveOffers(response.offers());
    }

    private void saveLegs(String searchId, List<FlightLegRequest> legs) {
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

    private void saveChildren(String searchId, List<Integer> childAges) {
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

    private void saveProviderStatuses(String searchId, List<ProviderSearchStatus> statuses) {
        for (ProviderSearchStatus status : statuses) {
            jdbcTemplate.update("""
                    INSERT INTO search.provider_search_statuses (
                        search_id, provider_code, call_status, duration_ms, status_message
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    searchId,
                    status.providerCode(),
                    status.status().name(),
                    status.durationMillis(),
                    status.message()
            );
        }
    }

    private void saveOffers(List<FlightOfferResponse> offers) {
        Instant createdAt = Instant.now();
        for (FlightOfferResponse offer : offers) {
            jdbcTemplate.update("""
                    INSERT INTO search.flight_offers (
                        offer_token, search_id, provider_code, price_amount, price_currency,
                        stop_count, total_duration_minutes, checked_at, expires_at,
                        affiliate_redirect_available, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    offer.offerToken(),
                    currentSearchIdForOffer(offer, offers),
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

    /*
     * The response object carries the search ID, but offers do not. Set it in the
     * repository before inserting offers using the active search ID.
     */
    private String currentSearchIdForOffer(FlightOfferResponse offer, List<FlightOfferResponse> offers) {
        return activeSearchId;
    }

    private String activeSearchId;

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
