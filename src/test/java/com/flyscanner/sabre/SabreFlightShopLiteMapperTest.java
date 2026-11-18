package com.flyscanner.sabre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.PassengerRequest;
import com.flyscanner.search.domain.CabinClass;
import com.flyscanner.search.domain.TripType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SabreFlightShopLiteMapperTest {
    private final SabreFlightShopLiteMapper mapper = new SabreFlightShopLiteMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsOneWayRequestAndPassengerTypesToSabreContract() {
        var request = request(TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", LocalDate.of(2027, 1, 15))),
                new PassengerRequest(2, List.of(6), 1));

        var payload = mapper.toRequestPayload(request, 30);

        assertEquals(1, ((List<?>) payload.get("journeys")).size());
        assertEquals(4, ((List<?>) payload.get("travelers")).size());
        assertEquals("ADT", ((java.util.Map<?, ?>)((List<?>) payload.get("travelers")).get(0)).get("passengerTypeCode"));
        assertEquals("CNN", ((java.util.Map<?, ?>)((List<?>) payload.get("travelers")).get(2)).get("passengerTypeCode"));
        assertEquals("INF", ((java.util.Map<?, ?>)((List<?>) payload.get("travelers")).get(3)).get("passengerTypeCode"));
        assertEquals(30, ((java.util.Map<?, ?>) payload.get("processingOptions")).get("limitNumberOfOffers"));
    }

    @Test
    void mapsSabreOfferFlightAndJourneyReferencesIntoFlyScannerDomain() throws Exception {
        String json = """
                {
                  "timestamp":"2027-01-01T10:00:00Z",
                  "flights":[
                    {
                      "id":"flight-1",
                      "departureAirportCode":"BLR",
                      "departureDate":"2027-01-15",
                      "departureTime":"08:30",
                      "arrivalAirportCode":"DXB",
                      "arrivalDate":"2027-01-15",
                      "arrivalTime":"11:00",
                      "marketingAirlineCode":"EK",
                      "marketingFlightNumber":501,
                      "durationInMinutes":150
                    }
                  ],
                  "journeys":[
                    {"id":"journey-1","flightRefs":["flight-1"],"requestedJourneyIndex":0}
                  ],
                  "offers":[
                    {
                      "type":"FlightOffer",
                      "id":"offer-1",
                      "validUntil":"2027-01-01T10:30:00Z",
                      "journeyRefs":["journey-1"],
                      "totalPrice":{"amount":"12500.00","currencyCode":"INR"}
                    }
                  ]
                }
                """;
        var root = objectMapper.readTree(json);

        var result = mapper.toSupplierResult(root, Instant.parse("2027-01-01T10:00:00Z"));

        assertEquals(1, result.offers().size());
        var offer = result.offers().getFirst();
        assertEquals("offer-1", offer.supplierReference());
        assertEquals("12500.00", offer.price().amount().toPlainString());
        assertEquals("INR", offer.price().currency());
        assertEquals("EK", offer.journeys().getFirst().segments().getFirst().carrierCode());
        assertEquals("501", offer.journeys().getFirst().segments().getFirst().flightNumber());
        assertEquals(0, offer.stopCount());
    }

    @Test
    void ignoresMalformedOffersMissingPriceOrJourney() throws Exception {
        var root = objectMapper.readTree("""
                {"flights":[],"journeys":[],"offers":[
                  {"id":"bad-price","journeyRefs":[]},
                  {"id":"bad-journey","totalPrice":{"amount":"100","currencyCode":"INR"},"journeyRefs":[]}
                ]}
                """);

        var result = mapper.toSupplierResult(root, Instant.parse("2027-01-01T10:00:00Z"));

        assertTrue(result.offers().isEmpty());
    }

    @Test
    void sabreErrorsAreRaisedAsProviderFailure() throws Exception {
        var root = objectMapper.readTree("""
                {"errors":[{"message":"No content available"}]}
                """);

        var exception = assertThrows(IllegalStateException.class,
                () -> mapper.toSupplierResult(root, Instant.now()));

        assertTrue(exception.getMessage().contains("No content available"));
    }

    private FlightSearchRequest request(TripType tripType, List<FlightLegRequest> legs, PassengerRequest passengers) {
        return new FlightSearchRequest(tripType, legs, passengers, CabinClass.ECONOMY, "INR", "IN", "en-IN");
    }
}
