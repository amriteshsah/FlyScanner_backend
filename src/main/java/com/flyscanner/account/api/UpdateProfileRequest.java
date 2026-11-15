package com.flyscanner.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 200) String fullName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$",
                message = "must be a valid phone number")
        String phoneNumber,
        @Past(message = "must be a date in the past")
        LocalDate dateOfBirth,
        @Pattern(regexp = "(?i)^$|^[A-Z]{2}$",
                message = "must be a two-letter country code")
        String nationality,
        @Pattern(regexp = "(?i)^$|^[A-Z]{3}$",
                message = "must be a three-letter IATA airport code")
        String homeAirport,
        @Pattern(regexp = "(?i)^$|^[A-Z]{3}$",
                message = "must be a three-letter currency code")
        String preferredCurrency
) {
}
