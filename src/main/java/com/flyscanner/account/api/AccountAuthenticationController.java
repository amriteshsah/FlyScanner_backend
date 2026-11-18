package com.flyscanner.account.api;

import com.flyscanner.account.application.AccountAuthenticationService;
import com.flyscanner.account.application.EmailOtpAuthenticationService;
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
@Tag(name = "Authentication", description = "Email OTP authentication, traveler profile and logout")
@RequestMapping("/api/v1/auth")
public class AccountAuthenticationController {

    private final AccountAuthenticationService authenticationService;
    private final EmailOtpAuthenticationService emailOtpAuthenticationService;

    public AccountAuthenticationController(
            AccountAuthenticationService authenticationService,
            EmailOtpAuthenticationService emailOtpAuthenticationService) {
        this.authenticationService = authenticationService;
        this.emailOtpAuthenticationService = emailOtpAuthenticationService;
    }

    // No public /register or /login endpoint exists for standard users.
    // Password verification remains in the service for the future Admin first factor.

    @Operation(summary = "Request a sign-in OTP for an existing or new email address")
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailOtpRequestResponse requestEmailOtp(@Valid @RequestBody EmailOtpRequest request) {
        return emailOtpAuthenticationService.requestCode(request.email());
    }

    @Operation(summary = "Resend the sign-in OTP after the resend cooldown")
    @PostMapping("/otp/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailOtpRequestResponse resendEmailOtp(@Valid @RequestBody EmailOtpRequest request) {
        return emailOtpAuthenticationService.resendCode(request.email());
    }

    @Operation(summary = "Verify an OTP and create or retrieve the standard-user account")
    @PostMapping("/otp/verify")
    public AuthenticationResponse verifyEmailOtp(@Valid @RequestBody VerifyEmailOtpRequest request) {
        return toResponse(emailOtpAuthenticationService.verifyCode(request.email(), request.code()));
    }

    @Operation(summary = "Get the currently authenticated user's profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public AccountPrincipal me(@AuthenticationPrincipal AccountPrincipal principal) {
        return authenticationService.getProfile(principal);
    }

    @Operation(summary = "Update optional traveler profile details or skip onboarding")
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
                result.accessToken(), result.expiresAt(), result.isNewUser(), result.profileCompleted());
    }
}
