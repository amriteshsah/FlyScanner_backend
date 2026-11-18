package com.flyscanner.search.domain;

import java.time.LocalDate;
import java.util.List;

public record FlightJourney(
        int legNumber,
        String origin,
        String destination,
        LocalDate departureDate,
        List<FlightSegment> segments,
        int durationMinutes,
        int stopCount
) {
    public FlightJourney {
        segments = List.copyOf(segments);
    }
}
