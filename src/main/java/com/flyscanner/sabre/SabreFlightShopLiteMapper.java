package com.flyscanner.sabre;

import com.fasterxml.jackson.databind.JsonNode;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.domain.FlightJourney;
import com.flyscanner.search.domain.FlightSegment;
import com.flyscanner.search.domain.Money;
import com.flyscanner.search.domain.SupplierOffer;
import com.flyscanner.search.domain.SupplierSearchResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SabreFlightShopLiteMapper {

    public Map<String, Object> toRequestPayload(FlightSearchRequest request, SabreProperties properties) {
        List<Map<String, Object>> journeys = request.legs().stream().map(leg -> Map.<String, Object>of(
                "departureLocation", Map.of("airportCode", leg.origin()),
                "arrivalLocation", Map.of("airportCode", leg.destination()),
                "departureDate", leg.departureDate().toString()
        )).toList();

        List<Map<String, Object>> travelers = new ArrayList<>();
        for (int i = 0; i < request.passengers().adults(); i++) {
            travelers.add(Map.of("passengerTypeCode", "ADT"));
        }
        if (request.passengers().childrenAges() != null) {
            for (Integer ignoredAge : request.passengers().childrenAges()) {
                travelers.add(Map.of("passengerTypeCode", "CNN"));
            }
        }
        for (int i = 0; i < request.passengers().infants(); i++) {
            travelers.add(Map.of("passengerTypeCode", "INF"));
        }

        Map<String, Object> cabin = Map.of("name", cabinName(request.cabinClass().name()));
        Map<String, Object> fare = Map.of("currencyCode", request.currency(), "cabin", cabin);
        Map<String, Object> processing = new HashMap<>();
        processing.put("limitNumberOfOffers", Math.max(1, Math.min(properties.getMaxOffers(), 1000)));
        if (properties.getPcc() != null && !properties.getPcc().isBlank()) {
            processing.put("pseudoCityCode", properties.getPcc());
        }

        return Map.of("journeys", journeys, "travelers", travelers,
                "fare", fare, "processingOptions", processing);
    }

    public SupplierSearchResult toSupplierResult(JsonNode root, Instant checkedAt) {
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            String message = errors.get(0).path("message").asText("Sabre search returned an error.");
            throw new IllegalStateException("Sabre Flight Shop Lite error: " + message);
        }

        Map<String, JsonNode> flightsById = new HashMap<>();
        for (JsonNode flight : root.path("flights")) {
            String id = flight.path("id").asText();
            if (!id.isBlank()) flightsById.put(id, flight);
        }

        Map<String, JsonNode> journeysById = new HashMap<>();
        for (JsonNode journey : root.path("journeys")) {
            String id = journey.path("id").asText();
            if (!id.isBlank()) journeysById.put(id, journey);
        }

        List<SupplierOffer> offers = new ArrayList<>();
        for (JsonNode offer : root.path("offers")) {
            String offerId = offer.path("id").asText();
            JsonNode totalPrice = offer.path("totalPrice");
            if (offerId.isBlank() || totalPrice.isMissingNode()
                    || totalPrice.path("amount").asText().isBlank()) {
                continue;
            }

            List<JsonNode> referencedJourneys = new ArrayList<>();
            JsonNode journeyRefs = offer.path("journeyRefs");
            if (journeyRefs.isArray()) {
                for (JsonNode ref : journeyRefs) {
                    JsonNode journey = journeysById.get(ref.asText());
                    if (journey != null) referencedJourneys.add(journey);
                }
            }
            if (referencedJourneys.isEmpty()) continue;

            List<FlightJourney> mappedJourneys = new ArrayList<>();
            int totalStops = 0;
            int totalDuration = 0;
            int legNumber = 1;
            for (JsonNode journey : referencedJourneys) {
                List<FlightSegment> segments = new ArrayList<>();
                JsonNode refs = journey.path("flightRefs");
                if (refs.isArray()) {
                    for (JsonNode ref : refs) {
                        JsonNode flight = flightsById.get(ref.asText());
                        if (flight != null) segments.add(toSegment(flight));
                    }
                }
                if (segments.isEmpty()) continue;

                FlightSegment first = segments.get(0);
                FlightSegment last = segments.get(segments.size() - 1);
                int stopCount = Math.max(0, segments.size() - 1);
                int duration = Math.max(0, (int) Duration.between(first.departureAt(), last.arrivalAt()).toMinutes());
                mappedJourneys.add(new FlightJourney(legNumber++,
                        first.origin(), last.destination(),
                        LocalDate.ofInstant(first.departureAt(), ZoneOffset.UTC),
                        segments, duration, stopCount));
                totalStops += stopCount;
                totalDuration += duration;
            }
            if (mappedJourneys.isEmpty()) continue;

            BigDecimal amount = new BigDecimal(totalPrice.path("amount").asText());
            String currency = totalPrice.path("currencyCode").asText("USD");
            Instant expiresAt = parseInstant(offer.path("validUntil").asText(), checkedAt.plus(Duration.ofMinutes(5)));
            offers.add(new SupplierOffer(offerId, new Money(amount, currency),
                    mappedJourneys, totalStops, totalDuration, expiresAt));
        }
        return new SupplierSearchResult(offers);
    }

    private FlightSegment toSegment(JsonNode flight) {
        LocalDate departureDate = LocalDate.parse(flight.path("departureDate").asText());
        LocalTime departureTime = LocalTime.parse(flight.path("departureTime").asText("00:00"));
        LocalDate arrivalDate = LocalDate.parse(flight.path("arrivalDate").asText());
        LocalTime arrivalTime = LocalTime.parse(flight.path("arrivalTime").asText("00:00"));

        // Sabre returns airport-local date/time. FlyScanner's current domain uses Instant;
        // UTC is a temporary normalization until airport timezone metadata is introduced.
        Instant departure = LocalDateTime.of(departureDate, departureTime).toInstant(ZoneOffset.UTC);
        Instant arrival = LocalDateTime.of(arrivalDate, arrivalTime).toInstant(ZoneOffset.UTC);
        int duration = Math.max(0, flight.path("durationInMinutes").asInt(
                (int) Duration.between(departure, arrival).toMinutes()));

        String carrier = flight.path("marketingAirlineCode").asText(
                flight.path("operatingAirlineCode").asText("UNKNOWN"));
        String number = flight.path("marketingFlightNumber").asText(
                flight.path("operatingFlightNumber").asText(""));
        return new FlightSegment(
                flight.path("departureAirportCode").asText(),
                flight.path("arrivalAirportCode").asText(),
                carrier,
                number,
                departure,
                arrival,
                duration
        );
    }

    private Instant parseInstant(String value, Instant fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String cabinName(String cabin) {
        return switch (cabin) {
            case "PREMIUM_ECONOMY" -> "Premium Economy";
            case "BUSINESS" -> "Business";
            case "FIRST" -> "First";
            default -> "Economy";
        };
    }
}
