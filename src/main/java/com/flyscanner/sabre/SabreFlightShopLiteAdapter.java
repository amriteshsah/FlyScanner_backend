package com.flyscanner.sabre;

import com.flyscanner.search.api.dto.FlightSearchRequest;
import com.flyscanner.search.domain.SupplierSearchResult;
import com.flyscanner.search.supplier.FlightSupplierAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "flyscanner.sabre", name = "enabled", havingValue = "true")
public class SabreFlightShopLiteAdapter implements FlightSupplierAdapter {
    private final RestClient restClient;
    private final SabreOAuthTokenService tokenService;
    private final SabreProperties properties;
    private final SabreFlightShopLiteMapper mapper = new SabreFlightShopLiteMapper();

    public SabreFlightShopLiteAdapter(RestClient.Builder builder,
                                     SabreOAuthTokenService tokenService,
                                     SabreProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenService = tokenService;
        this.properties = properties;
    }

    @Override
    public String providerCode() {
        return "SABRE";
    }

    @Override
    public SupplierSearchResult search(FlightSearchRequest request) {
        String token = tokenService.getAccessToken();
        Map<String, Object> response = restClient.post()
                .uri("/v1/offers/flightShopLite")
                .header("Authorization", "Bearer " + token)
                .body(mapper.toRequestPayload(request, properties))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null) {
            throw new IllegalStateException("Sabre Flight Shop Lite returned an empty response.");
        }
        return mapper.toSupplierResult(response, Instant.now());
    }
}
