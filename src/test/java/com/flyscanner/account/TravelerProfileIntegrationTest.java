package com.flyscanner.account;

import com.flyscanner.account.application.AccountAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TravelerProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccountAuthenticationService authenticationService;

    @Test
    void travelerProfileFieldsAreOptionalAndSavedForTheAuthenticatedAccount() throws Exception {
        String email = "traveler-profile-" + UUID.randomUUID() + "@example.test";
        var registration = authenticationService.register(
                email, "Profile Test User", "Traveler-Profile-Test-2026!");
        String token = registration.accessToken();
        UUID userId = registration.userId();

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
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
                .andExpect(jsonPath("$.preferredCurrency").value("INR"))
                .andExpect(jsonPath("$.profileCompleted").value(true));

        var saved = jdbcTemplate.queryForMap("""
                SELECT full_name, email, role, phone_number, date_of_birth,
                       nationality, home_airport, preferred_currency
                FROM account.users WHERE id = ?
                """, userId);
        assertEquals("Updated Profile User", saved.get("FULL_NAME"));
        assertEquals(email, saved.get("EMAIL"));
        assertEquals("USER", saved.get("ROLE"));
        assertEquals("+919876543210", saved.get("PHONE_NUMBER"));
        assertEquals(Date.valueOf("1990-06-15"), saved.get("DATE_OF_BIRTH"));
        assertEquals("IN", saved.get("NATIONALITY").toString().trim());
        assertEquals("BLR", saved.get("HOME_AIRPORT").toString().trim());
        assertEquals("INR", saved.get("PREFERRED_CURRENCY").toString().trim());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Profile User"))
                .andExpect(jsonPath("$.profileCompleted").value(true));
        assertNotNull(saved.get("DATE_OF_BIRTH"));
    }

    @Test
    void onboardingPayloadAcceptsAnInternationalPhoneWithSpacesAndAnIsoDate() throws Exception {
        String email = "onboarding-profile-" + UUID.randomUUID() + "@example.test";
        var registration = authenticationService.register(
                email, "Initial Traveler", "Traveler-Profile-Test-2026!");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + registration.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Amritesh Sah",
                                  "phoneNumber": "+91 98765 43210",
                                  "dateOfBirth": "1998-08-17"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Amritesh Sah"))
                .andExpect(jsonPath("$.phoneNumber").value("+91 98765 43210"))
                .andExpect(jsonPath("$.dateOfBirth").value("1998-08-17"))
                .andExpect(jsonPath("$.profileCompleted").value(true));
    }

    @Test
    void profilePatchCanContainOnlyOneOptionalField() throws Exception {
        String email = "partial-profile-" + UUID.randomUUID() + "@example.test";
        var registration = authenticationService.register(
                email, "Original Traveler", "Traveler-Profile-Test-2026!");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + registration.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+919876543210"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Original Traveler"))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"));
    }

    @Test
    void invalidTravelerProfileCodesAreRejectedWithBadRequest() throws Exception {
        String email = "invalid-profile-" + UUID.randomUUID() + "@example.test";
        var registration = authenticationService.register(
                email, "Profile Validation User", "Traveler-Profile-Test-2026!");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + registration.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nationality":"INDIA",
                                  "homeAirport":"BENGALURU",
                                  "preferredCurrency":"RUPEES"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
