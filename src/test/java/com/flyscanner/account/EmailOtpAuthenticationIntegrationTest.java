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

import java.time.Duration;
import java.time.Instant;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CapturingLoginOtpEmailSender emailSender;
    @Autowired private AccountAuthenticationService authenticationService;

    @Test
    void sendsFiveMinuteOtpEnforcesResendCooldownAndConsumesTheCodeOnce() throws Exception {
        String email = registerAccount("otp-lifecycle");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        String originalOtp = emailSender.latestCode(email);
        assertTrue(originalOtp.matches("[0-9]{6}"));
        assertEquals(1, emailSender.sentCount(email));

        Map<String, Object> challenge = challengeFor(email);
        Instant sentAt = ((java.time.OffsetDateTime) challenge.get("SENT_AT")).toInstant();
        Instant expiresAt = ((java.time.OffsetDateTime) challenge.get("EXPIRES_AT")).toInstant();
        assertEquals(Duration.ofMinutes(5), Duration.between(sentAt, expiresAt));
        assertNotEquals(originalOtp, challenge.get("OTP_HASH").toString());

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        assertEquals(1, emailSender.sentCount(email), "Cooldown must suppress an immediate resend");

        jdbcTemplate.update("""
                UPDATE account.email_login_otps SET sent_at = ?, expires_at = ? WHERE email = ?
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

        var verified = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s"}
                                """.formatted(email, replacementOtp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewUser").value(false))
                .andExpect(jsonPath("$.profileCompleted").value(true))
                .andReturn();
        String token = extractJsonString(verified.getResponse().getContentAsString(), "accessToken");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
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
    void expiredOtpCannotAuthenticate() throws Exception {
        String email = registerAccount("otp-expired");
        requestOtp(email);
        String otp = emailSender.latestCode(email);
        jdbcTemplate.update("""
                UPDATE account.email_login_otps SET sent_at = ?, expires_at = ? WHERE email = ?
                """, java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(6))),
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), email);
        verify(email, otp).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailReceivesTheSameGenericResponseAndCanRegisterAfterSuccessfulOtp() throws Exception {
        String email = "unknown-" + UUID.randomUUID() + "@example.test";
        requestOtp(email);
        assertEquals(1, emailSender.sentCount(email));
        assertEquals(0, accountCount(email), "The account must not be created before OTP verification.");

        verify(email, emailSender.latestCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.profileCompleted").value(false));
        assertEquals(1, accountCount(email));
    }

    @Test
    void fiveIncorrectCodesInvalidateTheChallenge() throws Exception {
        String email = registerAccount("otp-attempts");
        requestOtp(email);
        String correctCode = emailSender.latestCode(email);
        for (int attempt = 0; attempt < 5; attempt++) {
            verify(email, "000000").andExpect(status().isUnauthorized());
        }
        verify(email, correctCode).andExpect(status().isUnauthorized());
    }

    private String registerAccount(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.test";
        authenticationService.register(email, "OTP Test User", "Otp-Test-Password-2026!");
        return email;
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

    private Map<String, Object> challengeFor(String email) {
        return jdbcTemplate.queryForMap("""
                SELECT otp_hash, sent_at, expires_at FROM account.email_login_otps WHERE email = ?
                """, email);
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
    static class TestEmailConfiguration {
        @Bean @Primary
        CapturingLoginOtpEmailSender capturingLoginOtpEmailSender() {
            return new CapturingLoginOtpEmailSender();
        }
    }

    static class CapturingLoginOtpEmailSender implements LoginOtpEmailSender {
        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        @Override
        public void sendLoginOtp(String email, String fullName, String code, Instant expiresAt) {
            codes.put(email, code);
            counts.computeIfAbsent(email, ignored -> new AtomicInteger()).incrementAndGet();
        }

        String latestCode(String email) { return codes.get(email); }
        int sentCount(String email) { return counts.getOrDefault(email, new AtomicInteger()).get(); }
    }
}
