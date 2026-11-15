package com.flyscanner.saved.api;

import com.flyscanner.account.domain.AccountPrincipal;
import com.flyscanner.saved.application.SavedFlightService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Saved flights", description = "Save and manage flight offers for the authenticated traveler")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/saved-flights")
public class SavedFlightController {

    private final SavedFlightService service;

    public SavedFlightController(SavedFlightService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedFlightResponse save(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody SavedFlightRequest request) {
        return service.save(principal, request);
    }

    @GetMapping
    public List<SavedFlightResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.list(principal);
    }

    @DeleteMapping("/{savedFlightId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID savedFlightId) {
        service.remove(principal, savedFlightId);
    }
}
