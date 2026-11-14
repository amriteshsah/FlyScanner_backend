package com.flyscanner.search.api.dto;

import com.flyscanner.search.domain.FlightJourney;
import com.flyscanner.search.domain.Money;

import java.time.Instant;
import java.util.List;

public record FlightOfferResponse(
        String offerToken,
        String providerCode,
        Money price,
        List<FlightJourney> itinerary,
        int stops,
        int totalDurationMinutes,
        Instant checkedAt,
        Instant expiresAt,
        boolean affiliateRedirectAvailable
) {
    public FlightOfferResponse {
        itinerary = List.copyOf(itinerary);
    }
}
