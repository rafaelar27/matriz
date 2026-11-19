package com.matriz.email;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Chooses how invitations are delivered (TD-04, decided 2026-11-19). With MATRIZ_SMTP_HOST set, emails go over SMTP
 * (STARTTLS required unless MATRIZ_SMTP_STARTTLS=false). Without it, as in local development and CI, nothing is sent
 * and one log line records the invitation id, never the address (C2).
 *
 * <p>The sender is built here rather than through Spring Boot's mail auto-configuration, because that one is
 * switched on by an empty {@code spring.mail.host} as well.
 */
@Configuration
class MailConfig {

    private static final Logger LOG = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    InvitationMailer invitationMailer(@Value("${matriz.smtp.host:}") String host,
                                      @Value("${matriz.smtp.port:587}") int port,
                                      @Value("${matriz.smtp.username:}") String username,
                                      @Value("${matriz.smtp.password:}") String password,
                                      @Value("${matriz.smtp.starttls:true}") boolean starttls,
                                      @Value("${matriz.smtp.from:}") String from,
                                      @Value("${matriz.public-url:}") String publicUrl) {
        if (host.isBlank()) {
            LOG.info("No SMTP host is set (MATRIZ_SMTP_HOST): invitation emails are not sent, only logged.");
            return invitation -> LOG.info("Invitation {} created; email not sent because no SMTP host is set.",
                    invitation.invitationId());
        }
        if (from.isBlank()) {
            throw new IllegalStateException("MATRIZ_SMTP_FROM is required when MATRIZ_SMTP_HOST is set.");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.strip());
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        props.put("mail.smtp.starttls.enable", Boolean.toString(starttls));
        props.put("mail.smtp.starttls.required", Boolean.toString(starttls));
        // The server's TLS certificate must match the host name, so a forged certificate cannot capture the SMTP
        // credentials (security audit S-03, approved 2026-11-19).
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return smtp(sender, from.strip(), publicUrl);
    }

    /** Sends one plain-text email per invitation; any mail error becomes {@link InvitationNotSentException}. */
    static InvitationMailer smtp(org.springframework.mail.MailSender sender, String from, String publicUrl) {
        return invitation -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(invitation.to());
            message.setSubject(InvitationEmailText.subject(invitation));
            message.setText(InvitationEmailText.body(invitation, publicUrl));
            try {
                sender.send(message);
            } catch (MailException e) {
                LOG.warn("Invitation {} email could not be sent: {}", invitation.invitationId(),
                        e.getClass().getSimpleName());
                throw new InvitationNotSentException(e);
            }
        };
    }
}
