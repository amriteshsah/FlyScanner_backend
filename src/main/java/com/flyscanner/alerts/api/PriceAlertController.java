package com.flyscanner.alerts.api;

import com.flyscanner.account.domain.AccountPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.flyscanner.alerts.application.PriceAlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Price alerts", description = "Create and manage user-specific fare alerts")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/alerts")
public class PriceAlertController {

    private final PriceAlertService service;

    public PriceAlertController(PriceAlertService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceAlertResponse create(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PriceAlertRequest request) {
        return service.create(principal, request);
    }

    @GetMapping
    public List<PriceAlertResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.list(principal);
    }

    @PatchMapping("/{alertId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID alertId) {
        service.deactivate(principal, alertId);
    }
}
