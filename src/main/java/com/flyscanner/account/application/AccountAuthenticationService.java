package com.flyscanner.account.application;

import com.flyscanner.account.domain.AccountPrincipal;
import com.flyscanner.account.persistence.AccountRecord;
import com.flyscanner.account.persistence.JdbcAccountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountAuthenticationService {

    private final JdbcAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService tokens;
    private final Clock clock;

    public AccountAuthenticationService(
            JdbcAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            AccessTokenService tokens,
            Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Used only for controlled account provisioning, not by a public registration endpoint. */
    @Transactional
    public AuthenticationResult register(String email, String fullName, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (accounts.findByEmail(normalizedEmail).isPresent()) throw new EmailAlreadyRegisteredException();
        UUID userId = UUID.randomUUID();
        String normalizedName = fullName.trim();
        String passwordHash = passwordEncoder.encode(password);
        try {
            accounts.insert(userId, normalizedEmail, normalizedName, passwordHash, clock.instant());
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        return issueResult(userId, normalizedEmail, normalizedName, false, true);
    }

    /**
     * Admin portal first factor only. The future Admin flow must also verify
     * email OTP before issuing an authenticated session.
     */
    @Transactional(readOnly = true)
    public boolean verifyAdminPassword(String email, String password) {
        AccountRecord account = accounts.findByEmail(normalizeEmail(email)).orElse(null);
        return account != null
                && "ADMIN".equals(account.role())
                && account.passwordHash() != null
                && passwordEncoder.matches(password, account.passwordHash());
    }

    @Transactional
    public AuthenticationResult createOrGetOtpAccount(String email) {
        String normalizedEmail = normalizeEmail(email);
        AccountRecord existing = accounts.findByEmail(normalizedEmail).orElse(null);
        if (existing != null) {
            if ("ADMIN".equals(existing.role())) {
                throw new BadCredentialsException("Admin accounts require the Admin authentication flow.");
            }
            return issueResult(existing.id(), existing.email(), existing.fullName(),
                    false, existing.profileCompleted());
        }

        UUID userId = UUID.randomUUID();
        String displayName = displayNameFromEmail(normalizedEmail);
        Instant now = clock.instant();
        try {
            accounts.insertOtpAccount(userId, normalizedEmail, displayName, now);
        } catch (DuplicateKeyException race) {
            AccountRecord concurrent = accounts.findByEmail(normalizedEmail).orElseThrow(() -> race);
            if ("ADMIN".equals(concurrent.role())) {
                throw new BadCredentialsException("Admin accounts require the Admin authentication flow.");
            }
            return issueResult(concurrent.id(), concurrent.email(), concurrent.fullName(),
                    false, concurrent.profileCompleted());
        }
        return issueResult(userId, normalizedEmail, displayName, true, false);
    }

    @Transactional(readOnly = true)
    public AccountPrincipal getProfile(AccountPrincipal principal) {
        return accounts.findById(principal.userId())
                .map(this::toPrincipal)
                .orElseThrow(() -> new BadCredentialsException("Account is no longer active."));
    }

    @Transactional
    public AccountPrincipal updateProfile(
            AccountPrincipal principal,
            String fullName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String nationality,
            String homeAirport,
            String preferredCurrency) {
        String normalizedName = normalizeOptional(fullName);
        if (normalizedName != null && normalizedName.length() < 2) {
            throw new IllegalArgumentException("Full name must contain at least two characters.");
        }
        if (!accounts.updateProfile(
                principal.userId(),
                normalizedName,
                normalizeOptional(phoneNumber),
                dateOfBirth,
                normalizeCode(nationality),
                normalizeCode(homeAirport),
                normalizeCode(preferredCurrency),
                clock.instant())) {
            throw new BadCredentialsException("Account is no longer active.");
        }
        return accounts.findById(principal.userId())
                .map(this::toPrincipal)
                .orElseThrow(() -> new BadCredentialsException("Account is no longer active."));
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokens.revoke(authorizationHeader.substring(7).trim());
        }
    }

    private AccountPrincipal toPrincipal(AccountRecord account) {
        return new AccountPrincipal(
                account.id(), account.email(), account.fullName(), account.role(), account.createdAt(),
                account.phoneNumber(), account.dateOfBirth(), account.nationality(),
                account.homeAirport(), account.preferredCurrency(), account.profileCompleted());
    }

    private AuthenticationResult issueResult(
            UUID userId, String email, String fullName, boolean isNewUser, boolean profileCompleted) {
        AccessTokenService.IssuedToken token = tokens.issue(userId);
        return new AuthenticationResult(userId, email, fullName, token.value(), token.expiresAt(),
                isNewUser, profileCompleted);
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private String displayNameFromEmail(String email) {
        String localPart = email.substring(0, email.indexOf('@')).replaceAll("[._+-]+", " ").trim();
        if (localPart.isEmpty()) return "Traveler";
        return localPart.substring(0, 1).toUpperCase(Locale.ROOT) + localPart.substring(1);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeCode(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public record AuthenticationResult(
            UUID userId, String email, String fullName, String accessToken, Instant expiresAt,
            boolean isNewUser, boolean profileCompleted) { }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException() { super("An account with this email address already exists."); }
    }
}
