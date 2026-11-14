package com.flyscanner.account.api;

import com.flyscanner.account.application.AccountAuthenticationService;
import com.flyscanner.account.domain.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountAuthenticationController {

    private final AccountAuthenticationService authenticationService;

    public AccountAuthenticationController(AccountAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticationResponse register(@Valid @RequestBody RegisterRequest request) {
        var result = authenticationService.register(request.email(), request.fullName(), request.password());
        return toResponse(result);
    }

    @PostMapping("/login")
    public AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authenticationService.login(request.email(), request.password()));
    }

    @GetMapping("/me")
    public AccountPrincipal me(@org.springframework.security.core.annotation.AuthenticationPrincipal
                               AccountPrincipal principal) {
        return authenticationService.getProfile(principal);
    }

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
