package com.flyscanner.search.api.dto;

import com.flyscanner.search.domain.CabinClass;
import com.flyscanner.search.domain.TripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FlightSearchRequest(
        @NotNull TripType tripType,
        @NotEmpty @Size(max = 2) List<@NotNull @Valid FlightLegRequest> legs,
        @NotNull @Valid PassengerRequest passengers,
        @NotNull CabinClass cabinClass,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 currency code")
        String currency,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter market code")
        String market,
        @NotBlank @Size(max = 10) String locale
) {
    public FlightSearchRequest {
        legs = legs == null ? null : List.copyOf(legs);
    }
}
