package com.flyscanner.search.api;

import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.FlightSearchResponse;
import com.flyscanner.search.application.FlightSearchService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/searches", produces = MediaType.APPLICATION_JSON_VALUE)
public class FlightSearchController {

    private final FlightSearchService flightSearchService;

    public FlightSearchController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public FlightSearchResponse search(@Valid @RequestBody FlightSearchRequest request) {
        return flightSearchService.search(request);
    }
}
