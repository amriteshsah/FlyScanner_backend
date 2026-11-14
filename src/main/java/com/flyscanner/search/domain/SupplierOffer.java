package com.flyscanner.search.domain;

import java.time.Instant;
import java.util.List;

public record SupplierOffer(
        String supplierReference,
        Money price,
        List<FlightJourney> journeys,
        int stopCount,
        int totalDurationMinutes,
        Instant expiresAt
) {
    public SupplierOffer {
        journeys = List.copyOf(journeys);
    }
}
