package com.flyscanner.sabre;

import com.flyscanner.search.api.dto.FlightLegRequest;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.PassengerRequest;
import com.flyscanner.search.domain.CabinClass;
import com.flyscanner.search.domain.TripType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SabreFlightShopLiteMapperTest {
    private final SabreFlightShopLiteMapper mapper = new SabreFlightShopLiteMapper();

    @Test
    void mapsOneWayRequestAndPassengerTypesToSabreContract() {
        var request = request(TripType.ONE_WAY,
                List.of(new FlightLegRequest("BLR", "DXB", LocalDate.of(2027, 1, 15))),
                new PassengerRequest(2, List.of(6), 1));

        SabreProperties properties = new SabreProperties();
        properties.setMaxOffers(30);
        var payload = mapper.toRequestPayload(request, properties);

        assertEquals(1, ((List<?>) payload.get("journeys")).size());
        assertEquals(4, ((List<?>) payload.get("travelers")).size());
        assertEquals("ADT", ((Map<?, ?>)((List<?>) payload.get("travelers")).get(0)).get("passengerTypeCode"));
        assertEquals("CNN", ((Map<?, ?>)((List<?>) payload.get("travelers")).get(2)).get("passengerTypeCode"));
        assertEquals("INF", ((Map<?, ?>)((List<?>) payload.get("travelers")).get(3)).get("passengerTypeCode"));
        assertEquals(30, ((Map<?, ?>) payload.get("processingOptions")).get("limitNumberOfOffers"));
    }

    @Test
    void mapsSabreOfferFlightAndJourneyReferencesIntoFlyScannerDomain() {
        Map<String, Object> flight = Map.of(
                "id", "flight-1",
                "departureAirportCode", "BLR",
                "departureDate", "2027-01-15",
                "departureTime", "08:30",
                "arrivalAirportCode", "DXB",
                "arrivalDate", "2027-01-15",
                "arrivalTime", "11:00",
                "marketingAirlineCode", "EK",
                "marketingFlightNumber", 501,
                "durationInMinutes", 150
        );
        Map<String, Object> journey = Map.of(
                "id", "journey-1",
                "flightRefs", List.of("flight-1"),
                "requestedJourneyIndex", 0
        );
        Map<String, Object> offer = Map.of(
                "type", "FlightOffer",
                "id", "offer-1",
                "validUntil", "2027-01-01T10:30:00Z",
                "journeyRefs", List.of("journey-1"),
                "totalPrice", Map.of("amount", "12500.00", "currencyCode", "INR")
        );
        Map<String, Object> root = Map.of(
                "timestamp", "2027-01-01T10:00:00Z",
                "flights", List.of(flight),
                "journeys", List.of(journey),
                "offers", List.of(offer)
        );

        var result = mapper.toSupplierResult(root, Instant.parse("2027-01-01T10:00:00Z"));

        assertEquals(1, result.offers().size());
        var mappedOffer = result.offers().getFirst();
        assertEquals("offer-1", mappedOffer.supplierReference());
        assertEquals("12500.00", mappedOffer.price().amount().toPlainString());
        assertEquals("INR", mappedOffer.price().currency());
        assertEquals("EK", mappedOffer.journeys().getFirst().segments().getFirst().carrierCode());
        assertEquals("501", mappedOffer.journeys().getFirst().segments().getFirst().flightNumber());
        assertEquals(0, mappedOffer.stopCount());
    }

    @Test
    void ignoresMalformedOffersMissingPriceOrJourney() {
        Map<String, Object> root = Map.of(
                "flights", List.of(),
                "journeys", List.of(),
                "offers", List.of(
                        Map.of("id", "bad-price", "journeyRefs", List.of()),
                        Map.of("id", "bad-journey", "totalPrice", Map.of("amount", "100", "currencyCode", "INR"),
                                "journeyRefs", List.of())
                )
        );

        var result = mapper.toSupplierResult(root, Instant.parse("2027-01-01T10:00:00Z"));

        assertTrue(result.offers().isEmpty());
    }

    @Test
    void sabreErrorsAreRaisedAsProviderFailure() {
        Map<String, Object> root = Map.of(
                "errors", List.of(Map.of("message", "No content available"))
        );

        var exception = assertThrows(IllegalStateException.class,
                () -> mapper.toSupplierResult(root, Instant.now()));

        assertTrue(exception.getMessage().contains("No content available"));
    }

    private FlightSearchRequest request(TripType tripType, List<FlightLegRequest> legs, PassengerRequest passengers) {
        return new FlightSearchRequest(tripType, legs, passengers, CabinClass.ECONOMY, "INR", "IN", "en-IN");
    }
}
