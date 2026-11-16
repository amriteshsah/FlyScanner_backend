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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EmailOtpAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturingLoginOtpEmailSender emailSender;

    @Test
    void sendsFiveMinuteOtpAndEnforcesThreeMinuteResendCooldown() throws Exception {
        String email = registerAccount("otp-lifecycle");

        MvcResult request = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty())
                .andReturn();

        String originalOtp = emailSender.latestCode(email);
        assertTrue(originalOtp.matches("[0-9]{6}"));
        assertEquals(1, emailSender.sentCount(email));

        Map<String, Object> challenge = challengeFor(email);
        Instant sentAt = ((OffsetDateTime) challenge.get("SENT_AT")).toInstant();
        Instant expiresAt = ((OffsetDateTime) challenge.get("EXPIRES_AT")).toInstant();
        assertEquals(Duration.ofMinutes(5), Duration.between(sentAt, expiresAt));
        assertNotEquals(originalOtp, challenge.get("OTP_HASH").toString());

        String firstResendAvailableAt = extractJsonString(
                request.getResponse().getContentAsString(), "resendAvailableAt");

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resendAvailableAt").isNotEmpty());

        assertEquals(1, emailSender.sentCount(email),
                "A resend request before three minutes must not send another email");

        jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET sent_at = ?, expires_at = ?
                WHERE user_id = (SELECT id FROM account.users WHERE email = ?)
                """, java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(4))),
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofMinutes(1))), email);

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String replacementOtp = emailSender.latestCode(email);
        assertEquals(2, emailSender.sentCount(email));
        assertNotEquals(originalOtp, replacementOtp);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, originalOtp)))
                .andExpect(status().isUnauthorized());

        MvcResult verification = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, replacementOtp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andReturn();

        String accessToken = extractJsonString(
                verification.getResponse().getContentAsString(), "accessToken");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, replacementOtp)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredOtpCannotBeUsedToAuthenticate() throws Exception {
        String email = registerAccount("otp-expired");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String otp = emailSender.latestCode(email);
        Instant expiredSentAt = Instant.now().minus(Duration.ofMinutes(6));
        jdbcTemplate.update("""
                UPDATE account.email_login_otps
                SET sent_at = ?, expires_at = ?
                WHERE user_id = (SELECT id FROM account.users WHERE email = ?)
                """, java.sql.Timestamp.from(expiredSentAt),
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), email);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, otp)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailGetsTheSameGenericRequestResponseWithoutSendingEmail() throws Exception {
        String unknownEmail = "unknown-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(unknownEmail)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for this email, a sign-in code has been sent."));

        assertEquals(0, emailSender.sentCount(unknownEmail));
    }

    @Test
    void fiveIncorrectOtpAttemptsInvalidateTheChallenge() throws Exception {
        String email = registerAccount("otp-attempts");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String correctOtp = emailSender.latestCode(email);
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","code":"000000"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, correctOtp)))
                .andExpect(status().isUnauthorized());
    }

    private String registerAccount(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"OTP Test User","password":"Otp-Test-Password-2026!"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return email;
    }

    private Map<String, Object> challengeFor(String email) {
        return jdbcTemplate.queryForMap("""
                SELECT otp_hash, sent_at, expires_at
                FROM account.email_login_otps
                WHERE user_id = (SELECT id FROM account.users WHERE email = ?)
                """, email);
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

    @TestConfiguration
    static class TestEmailConfiguration {
        @Bean
        @Primary
        CapturingLoginOtpEmailSender capturingLoginOtpEmailSender() {
            return new CapturingLoginOtpEmailSender();
        }
    }

    static class CapturingLoginOtpEmailSender implements LoginOtpEmailSender {
        private final Map<String, String> latestCodes = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> sentCounts = new ConcurrentHashMap<>();

        @Override
        public void sendLoginOtp(String email, String fullName, String code, Instant expiresAt) {
            latestCodes.put(email, code);
            sentCounts.computeIfAbsent(email, ignored -> new AtomicInteger()).incrementAndGet();
        }

        String latestCode(String email) {
            return latestCodes.get(email);
        }

        int sentCount(String email) {
            return sentCounts.getOrDefault(email, new AtomicInteger()).get();
        }
    }
}
