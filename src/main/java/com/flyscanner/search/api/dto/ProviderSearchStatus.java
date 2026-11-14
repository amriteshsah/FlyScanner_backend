package com.flyscanner.search.api.dto;

import com.flyscanner.search.domain.ProviderCallStatus;

public record ProviderSearchStatus(
        String providerCode,
        ProviderCallStatus status,
        long latencyMs,
        String message
) {
}
