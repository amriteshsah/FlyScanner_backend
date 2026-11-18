package com.flyscanner.sabre;

import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "flyscanner.sabre", name = "enabled", havingValue = "true")
public class SabreOAuthTokenService {
    private final RestClient restClient;
    private final SabreProperties properties;
    private volatile CachedToken cachedToken;

    public SabreOAuthTokenService(SabreProperties properties) {
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && Instant.now().isBefore(current.expiresAt().minusSeconds(60))) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && Instant.now().isBefore(current.expiresAt().minusSeconds(60))) {
                return current.value();
            }
            CachedToken refreshed = requestToken();
            cachedToken = refreshed;
            return refreshed.value();
        }
    }

    private CachedToken requestToken() {
        if (properties.getUsername().isBlank() || properties.getGroup().isBlank()
                || properties.getDomain().isBlank() || properties.getPassword().isBlank()) {
            throw new IllegalStateException("Sabre credentials are not configured. Set SABRE_USERNAME, SABRE_GROUP, SABRE_DOMAIN and SABRE_PASSWORD.");
        }

        String epr = "V1:" + properties.getUsername() + ":" + properties.getGroup() + ":" + properties.getDomain();
        String encodedEpr = Base64.getEncoder().encodeToString(epr.getBytes(StandardCharsets.UTF_8));
        String encodedPassword = Base64.getEncoder().encodeToString(properties.getPassword().getBytes(StandardCharsets.UTF_8));
        String combined = encodedEpr + ":" + encodedPassword;
        String basicCredentials = Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));

        Map<?, ?> response = restClient.post()
                .uri(properties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic " + basicCredentials)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("access_token") instanceof String token) || token.isBlank()) {
            throw new IllegalStateException("Sabre OAuth returned an empty access token.");
        }
        long expiresIn = response.get("expires_in") instanceof Number value ? value.longValue() : 3600;
        return new CachedToken(token, Instant.now().plusSeconds(expiresIn));
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
