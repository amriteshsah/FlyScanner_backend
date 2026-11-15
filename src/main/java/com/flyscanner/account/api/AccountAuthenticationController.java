package com.flyscanner.account.api;

import com.flyscanner.account.application.AccountAuthenticationService;
import com.flyscanner.account.domain.AccountPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Authentication", description = "Account registration, login, profile and logout")
@RequestMapping("/api/v1/auth")
public class AccountAuthenticationController {

    private final AccountAuthenticationService authenticationService;

    public AccountAuthenticationController(AccountAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "Register a new FlyScanner account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticationResponse register(@Valid @RequestBody RegisterRequest request) {
        var result = authenticationService.register(request.email(), request.fullName(), request.password());
        return toResponse(result);
    }

    @Operation(summary = "Authenticate with email and password")
    @PostMapping("/login")
    public AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authenticationService.login(request.email(), request.password()));
    }

    @Operation(summary = "Get the currently authenticated user's profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public AccountPrincipal me(@AuthenticationPrincipal AccountPrincipal principal) {
        return authenticationService.getProfile(principal);
    }

    @Operation(summary = "Update the currently authenticated user's profile")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/me")
    public AccountPrincipal updateProfile(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authenticationService.updateProfile(
                principal,
                request.fullName(),
                request.phoneNumber(),
                request.dateOfBirth(),
                request.nationality(),
                request.homeAirport(),
                request.preferredCurrency()
        );
    }

    @Operation(summary = "Log out and revoke the current access token")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authenticationService.logout(authorization);
    }

    private AuthenticationResponse toResponse(AccountAuthenticationService.AuthenticationResult result) {
        return new AuthenticationResponse(result.userId(), result.email(), result.fullName(),
                result.accessToken(), result.expiresAt());
    }
}
