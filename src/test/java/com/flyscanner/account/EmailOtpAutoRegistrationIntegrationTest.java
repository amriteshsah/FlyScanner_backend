package com.flyscanner.account;

import com.flyscanner.account.application.AccountAuthenticationService;
import com.flyscanner.account.otp.LoginOtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EmailOtpAutoRegistrationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CapturingEmailSender emailSender;
    @Autowired private AccountAuthenticationService authenticationService;

    @Test
    void firstTimeUserGetsAnAccountOnlyAfterOtpAndCanSkipOptionalProfile() throws Exception {
        String email = "first-login-" + UUID.randomUUID() + "@example.test";
        requestOtp(email);
        assertEquals(0, accountCount(email), "Requesting an OTP must not create an account");

        MvcResult verification = verify(email, emailSender.latestCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.profileCompleted").value(false))
                .andReturn();
        assertEquals(1, accountCount(email));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT email, role, password_hash, profile_completed
                FROM account.users WHERE email = ?
                """, email);
        assertEquals(email, row.get("EMAIL"));
        assertEquals("USER", row.get("ROLE"));
        assertEquals(null, row.get("PASSWORD_HASH"));
        assertEquals(false, row.get("PROFILE_COMPLETED"));

        String token = extractJsonString(verification.getResponse().getContentAsString(), "accessToken");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.profileCompleted").value(false));

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(true));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCompleted").value(true));

        verify(email, emailSender.latestCode(email)).andExpect(status().isUnauthorized());
    }

    @Test
    void existingAccountUsesOtpWithoutBeingMarkedNew() throws Exception {
        String email = "existing-otp-" + UUID.randomUUID() + "@example.test";
        var created = authenticationService.register(email, "Existing Traveler", "Safe-Password-2026!");
        requestOtp(email);

        verify(email, emailSender.latestCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(created.userId().toString()))
                .andExpect(jsonPath("$.isNewUser").value(false))
                .andExpect(jsonPath("$.profileCompleted").value(true));
    }

    @Test
    void publicPasswordRoutesAreUnavailableAndAdminPasswordVerifierIsRetainedForFuturePortal() throws Exception {
        String email = "admin-auth-" + UUID.randomUUID() + "@example.test";
        String password = "Safe-Password-2026!";
        authenticationService.register(email, "Admin Candidate", password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Admin Candidate","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isNotFound());

        assertFalse(authenticationService.verifyAdminPassword(email, password));
        jdbcTemplate.update("UPDATE account.users SET role = 'ADMIN' WHERE email = ?", email);
        assertTrue(authenticationService.verifyAdminPassword(email, password));
    }

    private void requestOtp(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
    }

    private org.springframework.test.web.servlet.ResultActions verify(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","code":"%s"}
                        """.formatted(email, code)));
    }

    private int accountCount(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account.users WHERE email = ?",
                Integer.class, email);
    }

    private String extractJsonString(String json, String field) {
        String quote = Character.toString(34);
        String marker = quote + field + quote + ":" + quote;
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing JSON field: " + field);
        start += marker.length();
        int end = json.indexOf(34, start);
        if (end < 0) throw new AssertionError("Unterminated JSON field: " + field);
        return json.substring(start, end);
    }

    @TestConfiguration
    static class EmailConfiguration {
        @Bean @Primary
        CapturingEmailSender capturingEmailSender() { return new CapturingEmailSender(); }
    }

    static class CapturingEmailSender implements LoginOtpEmailSender {
        private final Map<String, String> codes = new ConcurrentHashMap<>();
        @Override
        public void sendLoginOtp(String email, String fullName, String code, Instant expiresAt) {
            codes.put(email, code);
        }
        String latestCode(String email) { return codes.get(email); }
    }
}
