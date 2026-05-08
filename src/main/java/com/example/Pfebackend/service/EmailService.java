package com.example.Pfebackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Réinitialisation de votre mot de passe - InvestIA");
        message.setText(
            "Bonjour,\n\n" +
            "Vous avez demandé la réinitialisation de votre mot de passe InvestIA.\n\n" +
            "Cliquez sur le lien ci-dessous pour définir un nouveau mot de passe :\n" +
            resetLink + "\n\n" +
            "Ce lien est valable pendant 1 heure.\n\n" +
            "Si vous n'avez pas fait cette demande, ignorez cet email — votre mot de passe reste inchangé.\n\n" +
            "L'équipe InvestIA"
        );
        mailSender.send(message);
    }
}
