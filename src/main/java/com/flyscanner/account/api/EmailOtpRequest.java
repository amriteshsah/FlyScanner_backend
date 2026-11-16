package com.flyscanner.account.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailOtpRequest(
        @NotBlank @Email @Size(max = 320) String email
) {
}
