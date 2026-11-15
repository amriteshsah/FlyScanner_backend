package com.flyscanner.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TravelerProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void travelerProfileIsSavedToDatabaseAndReturnedByTheAuthenticatedProfileEndpoint() throws Exception {
        String email = "traveler-profile-" + UUID.randomUUID() + "@example.test";
        String password = "Traveler-Profile-Test-2026!";

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Profile Test User",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();

        String registrationBody = registration.getResponse().getContentAsString();
        String accessToken = extractJsonString(registrationBody, "accessToken");
        String userId = extractJsonString(registrationBody, "userId");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Profile User",
                                  "phoneNumber": "+919876543210",
                                  "dateOfBirth": "1990-06-15",
                                  "nationality": "in",
                                  "homeAirport": "blr",
                                  "preferredCurrency": "inr",
                                  "email": "attempted-change@example.test",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Profile User"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-06-15"))
                .andExpect(jsonPath("$.nationality").value("IN"))
                .andExpect(jsonPath("$.homeAirport").value("BLR"))
                .andExpect(jsonPath("$.preferredCurrency").value("INR"));

        var persisted = jdbcTemplate.queryForMap("""
                SELECT full_name, email, role, phone_number, date_of_birth,
                       nationality, home_airport, preferred_currency
                FROM account.users
                WHERE id = ?
                """, UUID.fromString(userId));

        assertEquals("Updated Profile User", persisted.get("FULL_NAME").toString());
        assertEquals(email, persisted.get("EMAIL").toString());
        assertEquals("USER", persisted.get("ROLE").toString());
        assertEquals("+919876543210", persisted.get("PHONE_NUMBER").toString());
        assertEquals(Date.valueOf("1990-06-15"), persisted.get("DATE_OF_BIRTH"));
        assertEquals("IN", persisted.get("NATIONALITY").toString().trim());
        assertEquals("BLR", persisted.get("HOME_AIRPORT").toString().trim());
        assertEquals("INR", persisted.get("PREFERRED_CURRENCY").toString().trim());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Profile User"))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-06-15"))
                .andExpect(jsonPath("$.nationality").value("IN"))
                .andExpect(jsonPath("$.homeAirport").value("BLR"))
                .andExpect(jsonPath("$.preferredCurrency").value("INR"));

        assertNotNull(persisted.get("DATE_OF_BIRTH"));
    }

    @Test
    void invalidTravelerProfileCodesAreRejectedWithBadRequest() throws Exception {
        String email = "invalid-profile-" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Profile Validation User",
                                  "password": "Traveler-Profile-Test-2026!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = extractJsonString(
                registration.getResponse().getContentAsString(), "accessToken");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Profile Validation User",
                                  "nationality": "INDIA",
                                  "homeAirport": "BENGALURU",
                                  "preferredCurrency": "RUPEES"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private String extractJsonString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int valueStart = json.indexOf(marker);
        if (valueStart < 0) {
            throw new AssertionError("JSON response did not contain string field: " + field);
        }
        valueStart += marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new AssertionError("JSON string field was not terminated: " + field);
        }
        return json.substring(valueStart, valueEnd);
    }
}
