package com.flyscanner.alerts.api;

import com.flyscanner.search.domain.TripType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceAlertRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String origin,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String destination,
        @NotNull TripType tripType,
        @NotNull LocalDate departureDate,
        LocalDate returnDate,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal targetPrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 10) String market
) {
}
