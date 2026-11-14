package com.flyscanner.search.application;

import com.flyscanner.search.api.dto.FlightOfferResponse;
import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.api.dto.FlightSearchResponse;
import com.flyscanner.search.api.dto.ProviderSearchStatus;
import com.flyscanner.search.domain.FlightSearchStatus;
import com.flyscanner.search.domain.ProviderCallStatus;
import com.flyscanner.search.domain.SupplierOffer;
import com.flyscanner.search.domain.SupplierSearchResult;
import com.flyscanner.search.supplier.FlightSupplierAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class FlightSearchService {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchService.class);
    private static final String DISCLAIMER =
            "MOCK DATA ONLY: schedules and prices are illustrative, not live or bookable.";

    private final List<FlightSupplierAdapter> adapters;
    private final Executor supplierSearchExecutor;
    private final Clock clock;
    private final Duration providerTimeout;

    public FlightSearchService(
            List<FlightSupplierAdapter> adapters,
            @Qualifier("supplierSearchExecutor") Executor supplierSearchExecutor,
            Clock clock,
            @Value("$" + "{flyscanner.search.provider-timeout:3s}") Duration providerTimeout) {
        this.adapters = List.copyOf(adapters);
        this.supplierSearchExecutor = supplierSearchExecutor;
        this.clock = clock;
        this.providerTimeout = providerTimeout;
    }

    public FlightSearchResponse search(FlightSearchRequest request) {
        validateBusinessRules(request);
        Instant generatedAt = clock.instant();

        List<CompletableFuture<ProviderExecution>> futures = new ArrayList<>();
        for (FlightSupplierAdapter adapter : adapters) {
            futures.add(executeProvider(adapter, request));
        }

        List<ProviderExecution> executions = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        List<ProviderSearchStatus> statuses = executions.stream()
                .map(ProviderExecution::status)
                .toList();

        List<FlightOfferResponse> offers = executions.stream()
                .filter(execution -> execution.result() != null)
                .flatMap(execution -> execution.result().offers().stream()
                        .map(offer -> toResponse(execution.providerCode(), offer, generatedAt)))
                .sorted(Comparator.comparing((FlightOfferResponse offer) -> offer.price().amount())
                        .thenComparingInt(FlightOfferResponse::totalDurationMinutes))
                .toList();

        return new FlightSearchResponse(
                UUID.randomUUID(),
                generatedAt,
                determineStatus(executions, offers),
                statuses,
                offers,
                DISCLAIMER
        );
    }

    private CompletableFuture<ProviderExecution> executeProvider(
            FlightSupplierAdapter adapter, FlightSearchRequest request) {
        long startedAt = System.nanoTime();
        try {
            return CompletableFuture.supplyAsync(() -> {
                        SupplierSearchResult result = adapter.search(request);
                        ProviderCallStatus status = result.offers().isEmpty()
                                ? ProviderCallStatus.NO_RESULTS : ProviderCallStatus.SUCCESS;
                        return new ProviderExecution(
                                adapter.providerCode(),
                                result,
                                new ProviderSearchStatus(adapter.providerCode(), status,
                                        elapsedMillis(startedAt),
                                        status == ProviderCallStatus.SUCCESS
                                                ? "Offers returned." : "No offers found.")
                        );
                    }, supplierSearchExecutor)
                    .orTimeout(providerTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(exception -> {
                        Throwable cause = unwrap(exception);
                        if (cause instanceof TimeoutException) {
                            return new ProviderExecution(
                                    adapter.providerCode(),
                                    null,
                                    new ProviderSearchStatus(adapter.providerCode(),
                                            ProviderCallStatus.TIMEOUT,
                                            elapsedMillis(startedAt),
                                            "Provider response timed out.")
                            );
                        }
                        log.warn("Flight supplier {} failed", adapter.providerCode(), cause);
                        return new ProviderExecution(
                                adapter.providerCode(),
                                null,
                                new ProviderSearchStatus(adapter.providerCode(),
                                        ProviderCallStatus.ERROR,
                                        elapsedMillis(startedAt),
                                        "Provider request failed.")
                        );
                    });
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.completedFuture(new ProviderExecution(
                    adapter.providerCode(),
                    null,
                    new ProviderSearchStatus(adapter.providerCode(), ProviderCallStatus.ERROR,
                            elapsedMillis(startedAt), "Supplier executor is busy.")
            ));
        }
    }

    private FlightOfferResponse toResponse(
            String providerCode, SupplierOffer offer, Instant checkedAt) {
        return new FlightOfferResponse(
                UUID.randomUUID().toString(),
                providerCode,
                offer.price(),
                offer.journeys(),
                offer.stopCount(),
                offer.totalDurationMinutes(),
                checkedAt,
                offer.expiresAt(),
                false
        );
    }

    private FlightSearchStatus determineStatus(
            List<ProviderExecution> executions, List<FlightOfferResponse> offers) {
        if (!offers.isEmpty()) {
            boolean failedProvider = executions.stream().anyMatch(execution ->
                    execution.status().status() == ProviderCallStatus.ERROR
                            || execution.status().status() == ProviderCallStatus.TIMEOUT);
            return failedProvider ? FlightSearchStatus.PARTIAL_SUCCESS : FlightSearchStatus.SUCCESS;
        }

        boolean allUnavailable = !executions.isEmpty() && executions.stream().allMatch(execution ->
                execution.status().status() == ProviderCallStatus.ERROR
                        || execution.status().status() == ProviderCallStatus.TIMEOUT);
        return allUnavailable ? FlightSearchStatus.UNAVAILABLE : FlightSearchStatus.NO_RESULTS;
    }

    private void validateBusinessRules(FlightSearchRequest request) {
        if (request == null || request.tripType() == null || request.legs() == null
                || request.passengers() == null) {
            throw new IllegalArgumentException("Search request, trip type, legs and passengers are required.");
        }

        int expectedLegs = request.tripType().name().equals("ONE_WAY") ? 1 : 2;
        if (request.legs().size() != expectedLegs) {
            throw new IllegalArgumentException(
                    request.tripType() + " searches must contain exactly " + expectedLegs + " leg(s).");
        }

        for (int index = 0; index < request.legs().size(); index++) {
            var leg = request.legs().get(index);
            if (leg == null || leg.origin() == null || leg.destination() == null
                    || leg.departureDate() == null) {
                throw new IllegalArgumentException("Every leg requires origin, destination and departure date.");
            }
            if (leg.origin().equals(leg.destination())) {
                throw new IllegalArgumentException("Origin and destination must be different.");
            }
            if (index > 0 && leg.departureDate().isBefore(request.legs().get(index - 1).departureDate())) {
                throw new IllegalArgumentException("Return date cannot be before outbound date.");
            }
        }

        if (request.tripType().name().equals("ROUND_TRIP")) {
            var outbound = request.legs().get(0);
            var inbound = request.legs().get(1);
            if (!outbound.origin().equals(inbound.destination())
                    || !outbound.destination().equals(inbound.origin())) {
                throw new IllegalArgumentException("Return leg must reverse the outbound route.");
            }
        }

        int childCount = request.passengers().childrenAges() == null
                ? 0 : request.passengers().childrenAges().size();
        if (request.passengers().adults() < 1
                || request.passengers().infants() < 0
                || request.passengers().infants() > request.passengers().adults()
                || request.passengers().adults() + childCount + request.passengers().infants() > 9) {
            throw new IllegalArgumentException(
                    "Passenger total must not exceed 9 and infants cannot exceed adults.");
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private Throwable unwrap(Throwable exception) {
        if (exception instanceof CompletionException && exception.getCause() != null) {
            return exception.getCause();
        }
        return exception;
    }

    private record ProviderExecution(
            String providerCode,
            SupplierSearchResult result,
            ProviderSearchStatus status
    ) {
    }
}
