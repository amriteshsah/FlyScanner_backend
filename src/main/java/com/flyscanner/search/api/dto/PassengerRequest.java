package com.flyscanner.search.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PassengerRequest(
        @Min(1) @Max(9) int adults,
        @NotNull @Size(max = 8)
        List<@NotNull @Min(0) @Max(17) Integer> childrenAges,
        @Min(0) @Max(9) int infants
) {
    public PassengerRequest {
        childrenAges = childrenAges == null ? null : List.copyOf(childrenAges);
    }
}
