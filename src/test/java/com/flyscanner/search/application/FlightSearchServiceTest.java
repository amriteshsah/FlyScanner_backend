package com.flyscanner.search.application;

import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.PassengerRequest;
import com.flyscanner.search.domain.CabinClass;
import com.flyscanner.search.domain.FlightSearchStatus;
import com.flyscanner.search.domain.ProviderCallStatus;
import com.flyscanner.search.domain.TripType;
import com.flyscanner.search.supplier.mock.MockFlightSupplierAdapter;
import com.flyscanner.search.persistence.FlightSearchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FlightSearchServiceTest {

    private Clock clock;
    private FlightSearchService service;
    private FlightSearchResultRepository resultRepository;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-14T02:00:00Z"), ZoneOffset.UTC);
        resultRepository = mock(FlightSearchResultRepository.class);
        service = new FlightSearchService(
                List.of(new MockFlightSupplierAdapter()),
                Runnable::run,
                clock,
                Duration.ofSeconds(3),
                resultRepository
        );
    }

    @Test
    void returnsSortedSyntheticOffersForOneWaySearch() {
        var response = service.search(request(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", LocalDate.now(clock).plusDays(20)))
        ));

        assertEquals(FlightSearchStatus.SUCCESS, response.status());
        assertEquals("MOCK", response.providerStatuses().getFirst().providerCode());
        assertEquals(ProviderCallStatus.SUCCESS, response.providerStatuses().getFirst().status());
        assertEquals(3, response.offers().size());
        assertTrue(response.offers().get(0).price().amount()
                .compareTo(response.offers().get(1).price().amount()) < 0);
        assertEquals(1, response.offers().getFirst().itinerary().size());
        assertFalse(response.offers().getFirst().affiliateRedirectAvailable());
        assertTrue(response.disclaimer().contains("MOCK DATA ONLY"));
    }

    @Test
    void persistsTheSearchRequestAndResponse() {
        FlightSearchRequest request = request(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", LocalDate.now(clock).plusDays(20)))
        );

        var response = service.search(request);

        verify(resultRepository).save(request, response);
    }

    @Test
    void returnsTwoJourneysForRoundTripSearch() {
        LocalDate departure = LocalDate.now(clock).plusDays(20);
        var response = service.search(request(
                TripType.ROUND_TRIP,
                List.of(
                        new FlightLegRequest("BLR", "DXB", departure),
                        new FlightLegRequest("DXB", "BLR", departure.plusDays(7))
                )
        ));

        assertEquals(FlightSearchStatus.SUCCESS, response.status());
        assertEquals(2, response.offers().getFirst().itinerary().size());
    }

    @Test
    void calculatesTheSyntheticTotalForAllPassengers() {
        LocalDate departure = LocalDate.now(clock).plusDays(20);
        FlightSearchRequest request = new FlightSearchRequest(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", departure)),
                new PassengerRequest(2, List.of(6), 0),
                CabinClass.ECONOMY,
                "INR",
                "IN",
                "en-IN"
        );

        var response = service.search(request);

        assertEquals(3, response.offers().size());
        assertEquals(29_550L, response.offers().getFirst().price().amount().longValueExact());
    }


    @Test
    void acceptsAnInfantAndIncludesItInTheSupplierPassengerCount() {
        LocalDate departure = LocalDate.now(clock).plusDays(20);
        FlightSearchRequest request = new FlightSearchRequest(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", departure)),
                new PassengerRequest(1, List.of(), 1),
                CabinClass.ECONOMY,
                "INR",
                "IN",
                "en-IN"
        );

        var response = service.search(request);

        assertEquals(19_700L, response.offers().getFirst().price().amount().longValueExact());
        verify(resultRepository).save(request, response);
    }

    @Test
    void rejectsMoreInfantsThanAccompanyingAdults() {
        LocalDate departure = LocalDate.now(clock).plusDays(20);
        FlightSearchRequest request = new FlightSearchRequest(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", departure)),
                new PassengerRequest(1, List.of(), 2),
                CabinClass.ECONOMY,
                "INR",
                "IN",
                "en-IN"
        );

        assertThrows(IllegalArgumentException.class, () -> service.search(request));
    }

    @Test
    void rejectsACombinedPassengerCountAboveNine() {
        LocalDate departure = LocalDate.now(clock).plusDays(20);
        FlightSearchRequest request = new FlightSearchRequest(
                TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", departure)),
                new PassengerRequest(8, List.of(6), 1),
                CabinClass.ECONOMY,
                "INR",
                "IN",
                "en-IN"
        );

        assertThrows(IllegalArgumentException.class, () -> service.search(request));
    }

    @Test
    void rejectsRoundTripWithOnlyOneLeg() {
        var request = request(TripType.ROUND_TRIP,
                List.of(new FlightLegRequest("BLR", "DXB", LocalDate.now(clock).plusDays(20))));
        assertThrows(IllegalArgumentException.class, () -> service.search(request));
    }

    @Test
    void rejectsRouteWhereOriginEqualsDestination() {
        var request = request(TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "BLR", LocalDate.now(clock).plusDays(20))));
        assertThrows(IllegalArgumentException.class, () -> service.search(request));
    }

    private FlightSearchRequest request(TripType tripType, List<FlightLegRequest> legs) {
        return new FlightSearchRequest(
                tripType, legs, new PassengerRequest(1, List.of(), 0),
                CabinClass.ECONOMY, "INR", "IN", "en-IN"
        );
    }
}
