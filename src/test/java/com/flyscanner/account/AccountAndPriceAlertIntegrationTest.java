package com.flyscanner.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountAndPriceAlertIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void openApiDocumentIsAvailableWithoutAuthenticationAndDescribesBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FlyScanner API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/alerts'].get.security[0].bearerAuth").exists());
    }

    @Test
    void registrationLoginAndPriceAlertLifecycleRequireAndUseBearerToken() throws Exception {
        String email = "flyscanner-" + UUID.randomUUID() + "@example.test";
        String password = "Safe-Development-Password-2026!";

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"FlyScanner Test User","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String registrationBody = registration.getResponse().getContentAsString();
        String accessToken = extractJsonString(registrationBody, "accessToken");
        UUID userId = UUID.fromString(extractJsonString(registrationBody, "userId"));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM account.users WHERE email = ?",
                String.class, email);
        assertNotEquals(password, passwordHash);
        assertTrue(passwordEncoder.matches(password, passwordHash));

        Integer tokenHashLength = jdbcTemplate.queryForObject("""
                SELECT LENGTH(token_hash)
                FROM account.access_token_sessions
                WHERE user_id = ?
                """, Integer.class, userId);
        assertTrue(tokenHashLength != null && tokenHashLength == 64,
                "Only a SHA-256 token hash should be stored");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated FlyScanner User","email":"ignored@example.test","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated FlyScanner User"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated FlyScanner User"));

        mockMvc.perform(get("/api/v1/admin/test")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        String departureDate = LocalDate.now().plusDays(40).toString();
        MvcResult alertResult = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origin":"BLR",
                                  "destination":"DXB",
                                  "tripType":"ONE_WAY",
                                  "departureDate":"%s",
                                  "returnDate":null,
                                  "targetPrice":8500.00,
                                  "currency":"INR",
                                  "market":"IN"
                                }
                                """.formatted(departureDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.market").value("IN"))
                .andReturn();

        String alertId = extractJsonString(alertResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alertId));

        mockMvc.perform(patch("/api/v1/alerts/" + alertId + "/deactivate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationReturnsConflictAndWrongPasswordReturnsUnauthorized() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        String registerBody = """
                {"email":"%s","fullName":"Duplicate Test","password":"Safe-Development-Password-2026!"}
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password-000"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void priceAlertsCannotBeReadWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isUnauthorized());
    }

    private String extractJsonString(String json, String field) {
        String quote = Character.toString(34);
        String marker = quote + field + quote + ":" + quote;
        int valueStart = json.indexOf(marker);
        if (valueStart < 0) {
            throw new AssertionError("JSON response did not contain string field: " + field);
        }
        valueStart += marker.length();
        int valueEnd = json.indexOf(34, valueStart);
        if (valueEnd < 0) {
            throw new AssertionError("JSON string field was not terminated: " + field);
        }
        return json.substring(valueStart, valueEnd);
    }
}
