package com.flyscanner.account;

import com.flyscanner.account.application.AccountAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountAndPriceAlertIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountAuthenticationService authenticationService;

    @Test
    void corsPreflightAllowsTheCodespacesFrontendOriginForOtpRequest() throws Exception {
        String origin = "https://verbose-space-broccoli-vrpprrvjvv62r6q-5173.app.github.dev";
        mockMvc.perform(options("/api/v1/auth/otp/request")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", origin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Access-Control-Allow-Methods"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Access-Control-Allow-Headers"));
    }

    @Test
    void openApiExposesOtpProfileAndBearerEndpointsButNotStandardPasswordRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FlyScanner API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/otp/request'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/otp/verify'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/alerts'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").doesNotExist());
    }

    @Test
    void accountSessionAndPriceAlertLifecycleUseBearerTokenAndKeepPasswordHashesPrivate() throws Exception {
        String email = "flyscanner-" + UUID.randomUUID() + "@example.test";
        String password = "Safe-Development-Password-2026!";
        var registration = authenticationService.register(email, "FlyScanner Test User", password);
        String accessToken = registration.accessToken();
        UUID userId = registration.userId();

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM account.users WHERE email = ?", String.class, email);
        assertNotEquals(password, passwordHash);
        assertTrue(passwordEncoder.matches(password, passwordHash));

        Integer tokenHashLength = jdbcTemplate.queryForObject("""
                SELECT LENGTH(token_hash) FROM account.access_token_sessions WHERE user_id = ?
                """, Integer.class, userId);
        assertEquals(64, tokenHashLength);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.profileCompleted").value(true));

        mockMvc.perform(patch("/api/v1/auth/me")
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

        mockMvc.perform(get("/api/v1/admin/test").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        assertTrue(!authenticationService.verifyAdminPassword(email, password));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isNotFound());

        String departureDate = LocalDate.now().plusDays(40).toString();
        var alert = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "origin":"BLR","destination":"DXB","tripType":"ONE_WAY",
                                  "departureDate":"%s","returnDate":null,"targetPrice":8500.00,
                                  "currency":"INR","market":"IN"
                                }
                                """.formatted(departureDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.market").value("IN"))
                .andReturn();
        String alertId = extractJsonString(alert.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alertId));

        mockMvc.perform(patch("/api/v1/alerts/" + alertId + "/deactivate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void controlledRegistrationRejectsDuplicatesAndPublicPasswordRoutesAreUnavailable() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        String password = "Safe-Development-Password-2026!";
        authenticationService.register(email, "Duplicate Test", password);

        assertThrows(AccountAuthenticationService.EmailAlreadyRegisteredException.class,
                () -> authenticationService.register(email, "Duplicate Test", password));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Duplicate Test","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isNotFound());
    }

    @Test
    void priceAlertsCannotBeReadWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
    }

    private String extractJsonString(String json, String field) {
        String quote = Character.toString(34);
        String marker = quote + field + quote + ":" + quote;
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("JSON response did not contain " + field);
        start += marker.length();
        int end = json.indexOf(34, start);
        if (end < 0) throw new AssertionError("JSON field was not terminated: " + field);
        return json.substring(start, end);
    }
}
