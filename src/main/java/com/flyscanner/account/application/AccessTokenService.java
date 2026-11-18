package com.flyscanner.account.application;

import com.flyscanner.account.domain.AccountPrincipal;
import com.flyscanner.account.persistence.JdbcAccessTokenSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccessTokenService {

    private final JdbcAccessTokenSessionRepository sessions;
    private final Clock clock;
    private final Duration sessionTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccessTokenService(
            JdbcAccessTokenSessionRepository sessions,
            Clock clock,
            @Value("$" + "{flyscanner.auth.session-ttl:12h}") Duration sessionTtl) {
        this.sessions = sessions;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    public IssuedToken issue(UUID userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(sessionTtl);
        sessions.insert(hash(rawToken), userId, now, expiresAt);
        return new IssuedToken(rawToken, expiresAt);
    }

    public Optional<AccountPrincipal> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        return sessions.findActivePrincipal(hash(rawToken), clock.instant());
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank() && rawToken.length() <= 200) {
            sessions.revoke(hash(rawToken), clock.instant());
        }
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
