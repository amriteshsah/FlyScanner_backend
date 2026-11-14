package com.flyscanner.search.api.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record FlightLegRequest(
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter IATA airport code")
        String origin,
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter IATA airport code")
        String destination,
        @NotNull
        @FutureOrPresent(message = "must be today or a future date")
        LocalDate departureDate
) {
}
