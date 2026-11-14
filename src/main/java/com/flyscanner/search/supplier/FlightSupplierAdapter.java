package com.flyscanner.search.supplier;

import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.domain.SupplierSearchResult;

/**
 * Supplier boundary. Implementations must honour the supplier's commercial
 * terms, request limits, data-retention rules and affiliate policies.
 */
public interface FlightSupplierAdapter {
    String providerCode();
    SupplierSearchResult search(FlightSearchRequest request);
}
