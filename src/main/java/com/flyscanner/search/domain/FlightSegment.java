package com.flyscanner.search.domain;

import java.time.Instant;

public record FlightSegment(
        String origin,
        String destination,
        String carrierCode,
        String flightNumber,
        Instant departureAt,
        Instant arrivalAt,
        int durationMinutes
) {
}
