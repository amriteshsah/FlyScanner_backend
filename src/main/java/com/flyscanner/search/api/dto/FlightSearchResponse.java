package com.flyscanner.search.api.dto;

import com.flyscanner.search.domain.FlightSearchStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlightSearchResponse(
        UUID searchId,
        Instant generatedAt,
        FlightSearchStatus status,
        List<ProviderSearchStatus> providerStatuses,
        List<FlightOfferResponse> offers,
        String disclaimer
) {
    public FlightSearchResponse {
        providerStatuses = List.copyOf(providerStatuses);
        offers = List.copyOf(offers);
    }
}
