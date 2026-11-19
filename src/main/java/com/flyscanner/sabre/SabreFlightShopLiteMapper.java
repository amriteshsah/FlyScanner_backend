package com.flyscanner.sabre;

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

    public SupplierSearchResult toSupplierResult(Map<String, Object> root, Instant checkedAt) {
        Object errorsValue = root.get("errors");
        if (errorsValue instanceof List<?> errors && !errors.isEmpty()) {
            String message = string(asMap(errors.getFirst()).get("message"), "Sabre search returned an error.");
            throw new IllegalStateException("Sabre Flight Shop Lite error: " + message);
        }

        Map<String, Map<String, Object>> flightsById = indexById(root.get("flights"));
        Map<String, Map<String, Object>> journeysById = indexById(root.get("journeys"));

        List<SupplierOffer> offers = new ArrayList<>();
        for (Object offerValue : asList(root.get("offers"))) {
            Map<String, Object> offer = asMap(offerValue);
            String offerId = string(offer.get("id"), "");
            Map<String, Object> totalPrice = asMap(offer.get("totalPrice"));
            if (offerId.isBlank() || string(totalPrice.get("amount"), "").isBlank()) continue;

            List<Map<String, Object>> referencedJourneys = new ArrayList<>();
            for (Object refValue : asList(offer.get("journeyRefs"))) {
                String ref = string(refValue, "");
                Map<String, Object> journey = journeysById.get(ref);
                if (journey != null) referencedJourneys.add(journey);
            }
            if (referencedJourneys.isEmpty()) continue;

            List<FlightJourney> mappedJourneys = new ArrayList<>();
            int totalStops = 0;
            int totalDuration = 0;
            int legNumber = 1;
            for (Map<String, Object> journey : referencedJourneys) {
                List<FlightSegment> segments = new ArrayList<>();
                for (Object refValue : asList(journey.get("flightRefs"))) {
                    Map<String, Object> flight = flightsById.get(string(refValue, ""));
                    if (flight != null) segments.add(toSegment(flight));
                }
                if (segments.isEmpty()) continue;

                FlightSegment first = segments.getFirst();
                FlightSegment last = segments.getLast();
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

            BigDecimal amount = new BigDecimal(string(totalPrice.get("amount"), "0"));
            String currency = string(totalPrice.get("currencyCode"), "USD");
            Instant expiresAt = parseInstant(string(offer.get("validUntil"), ""), checkedAt.plus(Duration.ofMinutes(5)));
            offers.add(new SupplierOffer(offerId, new Money(amount, currency),
                    mappedJourneys, totalStops, totalDuration, expiresAt));
        }
        return new SupplierSearchResult(offers);
    }

    private FlightSegment toSegment(Map<String, Object> flight) {
        LocalDate departureDate = LocalDate.parse(string(flight.get("departureDate"), ""));
        LocalTime departureTime = LocalTime.parse(string(flight.get("departureTime"), "00:00"));
        LocalDate arrivalDate = LocalDate.parse(string(flight.get("arrivalDate"), ""));
        LocalTime arrivalTime = LocalTime.parse(string(flight.get("arrivalTime"), "00:00"));

        // Sabre returns airport-local date/time. FlyScanner's current domain uses Instant;
        // UTC is a temporary normalization until airport timezone metadata is introduced.
        Instant departure = LocalDateTime.of(departureDate, departureTime).toInstant(ZoneOffset.UTC);
        Instant arrival = LocalDateTime.of(arrivalDate, arrivalTime).toInstant(ZoneOffset.UTC);
        int duration = number(flight.get("durationInMinutes"),
                (int) Duration.between(departure, arrival).toMinutes());

        String carrier = string(flight.get("marketingAirlineCode"),
                string(flight.get("operatingAirlineCode"), "UNKNOWN"));
        String number = string(flight.get("marketingFlightNumber"),
                string(flight.get("operatingFlightNumber"), ""));
        return new FlightSegment(
                string(flight.get("departureAirportCode"), ""),
                string(flight.get("arrivalAirportCode"), ""),
                carrier,
                number,
                departure,
                arrival,
                Math.max(0, duration)
        );
    }

    private Map<String, Map<String, Object>> indexById(Object value) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Object item : asList(value)) {
            Map<String, Object> map = asMap(item);
            String id = string(map.get("id"), "");
            if (!id.isBlank()) result.put(id, map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
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
