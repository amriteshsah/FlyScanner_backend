package com.flyscanner.account.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 2, max = 200) String fullName,
        @NotBlank @Size(min = 12, max = 72,
                message = "must contain between 12 and 72 characters")
        String password
) {
}
