package com.flyscanner.account.application;

import com.flyscanner.account.api.EmailOtpRequestResponse;
import com.flyscanner.account.domain.AccountPrincipal;
import com.flyscanner.account.otp.JdbcLoginOtpRepository;
import com.flyscanner.account.otp.LoginOtpChallenge;
import com.flyscanner.account.otp.LoginOtpEmailSender;
import com.flyscanner.account.persistence.AccountRecord;
import com.flyscanner.account.persistence.JdbcAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class EmailOtpAuthenticationService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(3);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String GENERIC_MESSAGE =
            "If an account exists for this email, a sign-in code has been sent.";

    private final JdbcAccountRepository accounts;
    private final JdbcLoginOtpRepository challenges;
    private final LoginOtpEmailSender emailSender;
    private final AccessTokenService tokens;
    private final Clock clock;
    private final byte[] hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailOtpAuthenticationService(
            JdbcAccountRepository accounts,
            JdbcLoginOtpRepository challenges,
            LoginOtpEmailSender emailSender,
            AccessTokenService tokens,
            Clock clock,
            @Value("${flyscanner.auth.otp-hmac-secret}") String hmacSecret) {
        if (hmacSecret == null || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("The email OTP HMAC secret must contain at least 32 bytes.");
        }
        this.accounts = accounts;
        this.challenges = challenges;
        this.emailSender = emailSender;
        this.tokens = tokens;
        this.clock = clock;
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public EmailOtpRequestResponse requestCode(String email) {
        return issueOrResend(email);
    }

    @Transactional
    public EmailOtpRequestResponse resendCode(String email) {
        return issueOrResend(email);
    }

    private EmailOtpRequestResponse issueOrResend(String email) {
        Instant now = clock.instant();
        AccountRecord account = accounts.findByEmail(normalizeEmail(email)).orElse(null);
        if (account == null) {
            // Keep response shape and message identical for unknown email addresses.
            return new EmailOtpRequestResponse(GENERIC_MESSAGE,
                    now.plus(OTP_TTL), now.plus(RESEND_COOLDOWN));
        }

        // Serialize resend/request operations for this account, including the first insert.
        challenges.lockAccount(account.id());
        LoginOtpChallenge previous = challenges.findByUserIdForUpdate(account.id()).orElse(null);
        if (previous != null) {
            Instant resendAvailableAt = previous.sentAt().plus(RESEND_COOLDOWN);
            if (now.isBefore(resendAvailableAt)) {
                return new EmailOtpRequestResponse(
                        GENERIC_MESSAGE, previous.expiresAt(), resendAvailableAt);
            }
        }

        String code = generateCode();
        Instant expiresAt = now.plus(OTP_TTL);
        String digest = hash(account.id().toString() + ":" + code);
        challenges.save(account.id(), digest, now, expiresAt);

        // Do not include the OTP in the API response, database, or application logs.
        emailSender.sendLoginOtp(account.email(), account.fullName(), code, expiresAt);
        return new EmailOtpRequestResponse(GENERIC_MESSAGE, expiresAt, now.plus(RESEND_COOLDOWN));
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AccountAuthenticationService.AuthenticationResult verifyCode(String email, String code) {
        AccountRecord account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(EmailOtpAuthenticationService::invalidCode);

        challenges.lockAccount(account.id());
        LoginOtpChallenge challenge = challenges.findByUserIdForUpdate(account.id())
                .orElseThrow(EmailOtpAuthenticationService::invalidCode);

        Instant now = clock.instant();
        if (challenge.consumedAt() != null
                || !now.isBefore(challenge.expiresAt())
                || challenge.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw invalidCode();
        }

        String expected = hash(account.id() + ":" + code);
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                challenge.otpHash().trim().getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            challenges.recordFailedAttempt(account.id(), now);
            throw invalidCode();
        }

        // Consume inside the same locked transaction as access-token creation.
        challenges.consume(account.id(), now);
        AccessTokenService.IssuedToken token = tokens.issue(account.id());
        return new AccountAuthenticationService.AuthenticationResult(
                account.id(), account.email(), account.fullName(), token.value(), token.expiresAt());
    }

    private String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect the email sign-in code.", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BadCredentialsException invalidCode() {
        return new BadCredentialsException("The sign-in code is invalid or expired. Request a new code.");
    }
}
