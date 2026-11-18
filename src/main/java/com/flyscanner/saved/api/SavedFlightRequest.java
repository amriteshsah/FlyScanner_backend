package com.flyscanner.saved.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SavedFlightRequest(
        @NotBlank @Size(max = 36) String offerToken
) {
}
