package com.flyscanner.saved;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.flyscanner.account.application.AccountAuthenticationService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SavedFlightIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountAuthenticationService authenticationService;

    @Test
    void savesLoadsAndDeletesAFlightOfferForTheAuthenticatedUser() throws Exception {
        String email = "saved-flight-" + UUID.randomUUID() + "@example.test";
        AccountAuthenticationService.AuthenticationResult registration =
                authenticationService.register(email, "Saved Flight Test", "Safe-Development-Password-2026!");
        String token = registration.accessToken();
        String userId = registration.userId().toString();

        String departure = LocalDate.now().plusDays(35).toString();
        MvcResult search = mockMvc.perform(post("/api/v1/searches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tripType":"ONE_WAY",
                                  "legs":[{"origin":"BLR","destination":"DXB","departureDate":"%s"}],
                                  "passengers":{"adults":1,"childrenAges":[],"infants":0},
                                  "cabinClass":"ECONOMY",
                                  "currency":"INR",
                                  "market":"IN",
                                  "locale":"en-IN"
                                }
                                """.formatted(departure)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].offerToken").isNotEmpty())
                .andReturn();
        String offerToken = extractFirstArrayObjectString(
                search.getResponse().getContentAsString(), "offerToken");

        MvcResult saved = mockMvc.perform(post("/api/v1/saved-flights")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"offerToken":"%s"}
                                """.formatted(offerToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offerToken").value(offerToken))
                .andExpect(jsonPath("$.from").value("BLR"))
                .andExpect(jsonPath("$.to").value("DXB"))
                .andExpect(jsonPath("$.price").isNotEmpty())
                .andReturn();
        String savedId = extractJsonString(saved.getResponse().getContentAsString(), "id");

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM account.saved_flights
                WHERE user_id = ? AND offer_token = ?
                """, Integer.class, UUID.fromString(userId), offerToken);
        assertEquals(1, count);

        mockMvc.perform(get("/api/v1/saved-flights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(savedId))
                .andExpect(jsonPath("$[0].offerToken").value(offerToken));

        // Repeated save is idempotent for the same user and offer.
        mockMvc.perform(post("/api/v1/saved-flights")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"offerToken":"%s"}
                                """.formatted(offerToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(savedId));

        mockMvc.perform(delete("/api/v1/saved-flights/" + savedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Integer remaining = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM account.saved_flights WHERE id = ?
                """, Integer.class, UUID.fromString(savedId));
        assertTrue(remaining != null && remaining == 0);
    }

    @Test
    void savedFlightsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/saved-flights"))
                .andExpect(status().isUnauthorized());
    }

    private String extractJsonString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing JSON field: " + field);
        start += marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) throw new AssertionError("Unterminated JSON field: " + field);
        return json.substring(start, end);
    }

    private String extractFirstArrayObjectString(String json, String field) {
        int offers = json.indexOf("\"offers\":[");
        if (offers < 0) throw new AssertionError("Missing offers array");
        return extractJsonString(json.substring(offers), field);
    }
}
