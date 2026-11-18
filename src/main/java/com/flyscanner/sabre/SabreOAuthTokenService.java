package com.flyscanner.sabre;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class SabreOAuthTokenService {
    private final RestClient restClient;
    private final SabreProperties properties;
    private volatile CachedToken cachedToken;

    public SabreOAuthTokenService(RestClient.Builder builder, SabreProperties properties) {
        this.restClient = builder.build();
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

        TokenResponse response = restClient.post()
                .uri(properties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic " + basicCredentials)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Sabre OAuth returned an empty access token.");
        }
        long expiresIn = response.expiresIn() > 0 ? response.expiresIn() : 3600;
        return new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresIn));
    }

    private record TokenResponse(String access_token, long expires_in, String token_type) {
        String accessToken() { return access_token; }
        long expiresIn() { return expires_in; }
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
