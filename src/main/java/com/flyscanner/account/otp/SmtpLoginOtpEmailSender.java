package com.flyscanner.account.otp;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class SmtpLoginOtpEmailSender implements LoginOtpEmailSender {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm z").withZone(ZoneId.of("UTC"));

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String fromAddress;

    public SmtpLoginOtpEmailSender(
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${flyscanner.auth.email-from:no-reply@flyscanner.local}") String fromAddress) {
        this.mailSenders = mailSenders;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendLoginOtp(String email, String fullName, String code, Instant expiresAt) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            throw new OtpEmailDeliveryException(
                    "Email delivery is not configured. Set SMTP_HOST and the SMTP credentials.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Your FlyScanner sign-in code");
        message.setText("""
                Hello %s,

                Your FlyScanner sign-in code is: %s

                This code expires in 5 minutes (at %s UTC) and can only be used once.
                For your security, do not share this code with anyone.

                If you did not request this code, you can ignore this email.

                FlyScanner
                """.formatted(fullName, code, EXPIRY_FORMAT.format(expiresAt)));

        try {
            sender.send(message);
        } catch (RuntimeException exception) {
            throw new OtpEmailDeliveryException("Unable to deliver the sign-in email. Please try again.", exception);
        }
    }

    public static class OtpEmailDeliveryException extends RuntimeException {
        public OtpEmailDeliveryException(String message) {
            super(message);
        }

        public OtpEmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
