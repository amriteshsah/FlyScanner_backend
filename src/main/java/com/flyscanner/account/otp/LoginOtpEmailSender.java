package com.flyscanner.account.otp;

import java.time.Instant;

public interface LoginOtpEmailSender {
    void sendLoginOtp(String email, String fullName, String code, Instant expiresAt);
}
