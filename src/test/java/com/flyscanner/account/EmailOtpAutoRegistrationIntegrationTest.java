package com.flyscanner.account;

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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void anUnregisteredEmailCreatesAnAccountOnlyAfterOtpVerification() throws Exception {
        String email = "first-login-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        assertEquals(1, emailSender.sentCount(email));
        assertEquals(0, accountCount(email), "Requesting an OTP must not create an account");

        String otp = emailSender.latestCode(email);
        var verification = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.profileCompleted").value(false))
                .andReturn();

        assertEquals(1, accountCount(email), "The account is created after successful verification");
        Map<String, Object> account = jdbcTemplate.queryForMap("""
                SELECT email, role, password_hash, profile_completed
                FROM account.users WHERE email = ?
                """, email);
        assertEquals(email, account.get("EMAIL"));
        assertEquals("USER", account.get("ROLE"));
        assertEquals(null, account.get("PASSWORD_HASH"));
        assertEquals(false, account.get("PROFILE_COMPLETED"));

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

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExistingAccountCanStillAuthenticateWithOtpAndIsNotMarkedNew() throws Exception {
        String email = "existing-otp-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Existing Traveler","password":"Safe-Password-2026!"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, emailSender.latestCode(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false))
                .andExpect(jsonPath("$.profileCompleted").value(true));
    }

    @Test
    void passwordLoginIsRejectedForStandardUsersButTheAdminCredentialVerifierRemainsUsable() throws Exception {
        String email = "password-user-" + UUID.randomUUID() + "@example.test";
        String password = "Safe-Password-2026!";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Standard Traveler","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update("UPDATE account.users SET role = 'ADMIN' WHERE email = ?", email);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void profilePatchAcceptsOptionalFieldsWithoutOverwritingValuesThatWereNotSent() throws Exception {
        String email = "optional-profile-" + UUID.randomUUID() + "@example.test";
        var registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Original Traveler","password":"Safe-Password-2026!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = extractJsonString(registration.getResponse().getContentAsString(), "accessToken");

        mockMvc.perform(patch("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+919876543210"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Original Traveler"))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"));
    }

    private int accountCount(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account.users WHERE email = ?", Integer.class, email);
    }

    private String extractJsonString(String json, String field) {
        String marker = "\\\"" + field + "\\":\\\"";
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing JSON field: " + field);
        start += marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) throw new AssertionError("Unterminated JSON field: " + field);
        return json.substring(start, end);
    }

    @TestConfiguration
    static class EmailConfiguration {
        @Bean @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static class CapturingEmailSender implements LoginOtpEmailSender {
        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, Integer> counts = new ConcurrentHashMap<>();

        @Override
        public void sendLoginOtp(String email, String fullName, String code, Instant expiresAt) {
            codes.put(email, code);
            counts.merge(email, 1, Integer::sum);
        }

        String latestCode(String email) { return codes.get(email); }
        int sentCount(String email) { return counts.getOrDefault(email, 0); }
    }
}
