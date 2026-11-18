package com.flyscanner.saved.application;

import com.flyscanner.account.domain.AccountPrincipal;
import com.flyscanner.saved.api.SavedFlightRequest;
import com.flyscanner.saved.api.SavedFlightResponse;
import com.flyscanner.saved.persistence.JdbcSavedFlightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class SavedFlightService {

    private final JdbcSavedFlightRepository savedFlights;
    private final Clock clock;

    public SavedFlightService(JdbcSavedFlightRepository savedFlights, Clock clock) {
        this.savedFlights = savedFlights;
        this.clock = clock;
    }

    @Transactional
    public SavedFlightResponse save(AccountPrincipal principal, SavedFlightRequest request) {
        return savedFlights.save(principal.userId(), request.offerToken(), clock.instant())
                .orElseThrow(SavedOfferNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<SavedFlightResponse> list(AccountPrincipal principal) {
        return savedFlights.findAllByUser(principal.userId());
    }

    @Transactional
    public void remove(AccountPrincipal principal, UUID savedFlightId) {
        if (!savedFlights.delete(savedFlightId, principal.userId())) {
            throw new SavedFlightNotFoundException();
        }
    }

    public static class SavedOfferNotFoundException extends RuntimeException {
        public SavedOfferNotFoundException() {
            super("The flight offer was not found or has expired from the search inventory.");
        }
    }

    public static class SavedFlightNotFoundException extends RuntimeException {
        public SavedFlightNotFoundException() {
            super("Saved flight was not found.");
        }
    }
}
