package com.flyscanner.search.persistence;

import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.FlightSearchResponse;

/**
 * Persists a search request and the response returned to the caller.
 */
public interface FlightSearchResultRepository {

    void save(FlightSearchRequest request, FlightSearchResponse response);
}
