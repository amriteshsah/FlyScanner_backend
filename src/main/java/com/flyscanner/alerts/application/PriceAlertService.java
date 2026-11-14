package com.flyscanner.alerts.application;

import com.flyscanner.alerts.api.PriceAlertRequest;
import com.flyscanner.alerts.api.PriceAlertResponse;
import com.flyscanner.alerts.persistence.JdbcPriceAlertRepository;
import com.flyscanner.account.domain.AccountPrincipal;
import com.flyscanner.search.domain.TripType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PriceAlertService {

    private final JdbcPriceAlertRepository alerts;
    private final Clock clock;

    public PriceAlertService(JdbcPriceAlertRepository alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    @Transactional
    public PriceAlertResponse create(AccountPrincipal principal, PriceAlertRequest request) {
        if (request.origin().equals(request.destination())) {
            throw new IllegalArgumentException("Origin and destination must be different.");
        }
        if (request.departureDate().isBefore(java.time.LocalDate.now(clock))) {
            throw new IllegalArgumentException("Departure date must be today or a future date.");
        }
        if (request.tripType() == TripType.ROUND_TRIP) {
            if (request.returnDate() == null || request.returnDate().isBefore(request.departureDate())) {
                throw new IllegalArgumentException("A round-trip alert requires a return date on or after departure.");
            }
        } else if (request.returnDate() != null) {
            throw new IllegalArgumentException("A one-way alert must not include a return date.");
        }

        Instant now = clock.instant();
        return alerts.insert(UUID.randomUUID(), principal.userId(),
                request.origin(), request.destination(), request.tripType(),
                request.departureDate(), request.returnDate(), request.targetPrice(),
                request.currency(), now, request.departureDate().atStartOfDay()
                        .plusDays(1).toInstant(java.time.ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<PriceAlertResponse> list(AccountPrincipal principal) {
        return alerts.findAllByUser(principal.userId());
    }

    @Transactional
    public void deactivate(AccountPrincipal principal, UUID alertId) {
        if (!alerts.deactivate(alertId, principal.userId())) {
            throw new AlertNotFoundException();
        }
    }

    public static class AlertNotFoundException extends RuntimeException {
        public AlertNotFoundException() {
            super("Price alert was not found.");
        }
    }
}
