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

    @Transactional
    public AuthenticationResult register(String email, String fullName, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (accounts.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        UUID userId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(password);
        try {
            accounts.insert(userId, normalizedEmail, fullName.trim(), passwordHash, clock.instant());
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        return issueResult(userId, normalizedEmail, fullName.trim());
    }

    @Transactional
    public AuthenticationResult login(String email, String password) {
        AccountRecord account = accounts.findByEmail(normalizeEmail(email))
                .orElseThrow(BadCredentialsException::new);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return issueResult(account.id(), account.email(), account.fullName());
    }

    public AccountPrincipal getProfile(AccountPrincipal principal) {
        return principal;
    }

    public void logout(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokens.revoke(authorizationHeader.substring(7).trim());
        }
    }

    private AuthenticationResult issueResult(UUID userId, String email, String fullName) {
        AccessTokenService.IssuedToken token = tokens.issue(userId);
        return new AuthenticationResult(userId, email, fullName, token.value(), token.expiresAt());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthenticationResult(
            UUID userId, String email, String fullName, String accessToken, java.time.Instant expiresAt) {
    }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException() {
            super("An account with this email address already exists.");
        }
    }
}
