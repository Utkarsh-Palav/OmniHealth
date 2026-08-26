package com.omnihealth.platform.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String recipientEmail, String rawToken) {
        String verificationUrl = "http://localhost:8080/api/v1/platform/users/verify-email?token=" + rawToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(recipientEmail);
        message.setSubject("Verify your OmniHealth Account");
        message.setText("Click the following link to verify your email address:\n" + verificationUrl);

        mailSender.send(message);
        log.info("Verification email sent successfully to {}", recipientEmail);
    }
}
