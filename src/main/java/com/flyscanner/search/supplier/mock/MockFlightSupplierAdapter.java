package com.flyscanner.search.supplier.mock;

import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.domain.FlightJourney;
import com.flyscanner.search.domain.FlightSegment;
import com.flyscanner.search.domain.Money;
import com.flyscanner.search.domain.SupplierOffer;
import com.flyscanner.search.domain.SupplierSearchResult;
import com.flyscanner.search.supplier.FlightSupplierAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Synthetic fixture data for development. These fares and schedules are not bookable. */
@Component
@ConditionalOnProperty(prefix = "flyscanner.search", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockFlightSupplierAdapter implements FlightSupplierAdapter {

    private static final int[] ONE_WAY_PRICES_INR = {12_650, 11_350, 9_850};
    private static final int[] ROUND_TRIP_PRICES_INR = {24_500, 21_900, 19_900};
    private static final int[] JOURNEY_DURATIONS = {260, 380, 510};
    private static final String[] CONNECTION_AIRPORTS = {"DOH", "MCT"};

    @Override
    public String providerCode() {
        return "MOCK";
    }

    @Override
    public SupplierSearchResult search(FlightSearchRequest request) {
        if (!"INR".equals(request.currency())) {
            throw new IllegalArgumentException("Mock supplier currently supports INR only; no currency conversion is performed.");
        }

        Instant checkedAt = Instant.now();
        int[] prices = request.legs().size() == 2 ? ROUND_TRIP_PRICES_INR : ONE_WAY_PRICES_INR;
        int partySize = request.passengers().adults()
                + request.passengers().childrenAges().size()
                + request.passengers().infants();
        List<SupplierOffer> offers = new ArrayList<>();

        for (int option = 0; option < prices.length; option++) {
            List<FlightJourney> journeys = new ArrayList<>();
            int totalStops = 0;
            int totalDuration = 0;

            for (int legIndex = 0; legIndex < request.legs().size(); legIndex++) {
                FlightJourney journey = createJourney(request.legs().get(legIndex), legIndex + 1, option);
                journeys.add(journey);
                totalStops += journey.stopCount();
                totalDuration += journey.durationMinutes();
            }

            offers.add(new SupplierOffer(
                    "MOCK-" + UUID.randomUUID(),
                    new Money(BigDecimal.valueOf((long) prices[option] * partySize).setScale(2), "INR"),
                    journeys,
                    totalStops,
                    totalDuration,
                    checkedAt.plus(Duration.ofMinutes(10))
            ));
        }

        return new SupplierSearchResult(offers);
    }

    private FlightJourney createJourney(FlightLegRequest leg, int legNumber, int option) {
        int stops = option;
        int[] segmentDurations = switch (stops) {
            case 0 -> new int[]{JOURNEY_DURATIONS[option]};
            case 1 -> new int[]{145, 145};
            default -> new int[]{130, 130, 140};
        };

        LocalDateTime departure = leg.departureDate().atTime(7 + (option * 3), 0);
        Instant firstDeparture = departure.toInstant(ZoneOffset.UTC);
        Instant cursor = firstDeparture;
        String currentAirport = leg.origin();
        List<FlightSegment> segments = new ArrayList<>();

        for (int index = 0; index < segmentDurations.length; index++) {
            boolean lastSegment = index == segmentDurations.length - 1;
            String nextAirport = lastSegment ? leg.destination() : CONNECTION_AIRPORTS[index];
            Instant arrival = cursor.plus(Duration.ofMinutes(segmentDurations[index]));

            segments.add(new FlightSegment(
                    currentAirport,
                    nextAirport,
                    "FS",
                    "FS" + (100 + (option * 10) + index),
                    cursor,
                    arrival,
                    segmentDurations[index]
            ));

            currentAirport = nextAirport;
            cursor = lastSegment
                    ? arrival
                    : arrival.plus(Duration.ofMinutes(stops == 1 ? 90 : 55));
        }

        int duration = (int) Duration.between(firstDeparture, cursor).toMinutes();
        return new FlightJourney(
                legNumber,
                leg.origin(),
                leg.destination(),
                leg.departureDate(),
                segments,
                duration,
                stops
        );
    }
}
