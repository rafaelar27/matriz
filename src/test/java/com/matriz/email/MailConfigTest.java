package com.matriz.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

/**
 * How invitations are delivered, as pure logic (INC-17; US-03; FR-03, AC-03.1, NFR-02, C2; TD-04 and decision QJ of
 * 2026-11-19). No Spring context, no database, no network: the mail sender is a mock. Expected: with no SMTP host
 * nothing is sent and exactly one log line carries the invitation id but never the address; with a host, one plain
 * message goes to the invited address; a mail error becomes InvitationNotSentException and is logged without the
 * address.
 */
class MailConfigTest {

    private static final String ADDRESS = "someone.private@example.com";
    private static final UUID ID = UUID.fromString("a0000000-0000-0000-0000-0000000000e2");
    private static final InvitationMailer.Invitation INVITATION = new InvitationMailer.Invitation(ID, ADDRESS,
            "Harbor Works", "Carla Demo", "Viewer");

    private final Logger logger = (Logger) LoggerFactory.getLogger(MailConfig.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
        logger.setLevel(previousLevel);
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("AC-INC17.9, FR-03, C2 · with no SMTP host, sending only logs one line with the invitation id and never the email address")
    void noHostLogsTheIdOnly() {
        InvitationMailer mailer = new MailConfig().invitationMailer("", 587, "", "", true, "", "");
        logs.list.clear(); // the line written when the mailer is built is not about an invitation
        mailer.send(INVITATION);

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains(ID.toString());
        assertThat(messages()).noneMatch(m -> m.contains(ADDRESS) || m.contains("@"));
    }

    @Test
    @DisplayName("AC-INC17.9, C2 · building the log-only mailer logs no email address either")
    void buildingTheLogOnlyMailerLogsNoAddress() {
        new MailConfig().invitationMailer("   ", 587, "", "", true, "", "");
        assertThat(messages()).noneMatch(m -> m.contains("@"));
    }

    @Test
    @DisplayName("AC-INC17.9 · an SMTP host without a sender address (MATRIZ_SMTP_FROM) stops the application from starting")
    void hostWithoutSenderIsRefused() {
        assertThatThrownBy(() -> new MailConfig().invitationMailer("smtp.example.test", 587, "", "", true, "", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MATRIZ_SMTP_FROM");
    }

    @Test
    @DisplayName("AC-INC17.1, AC-03.1, FR-03 · with a mail server, one message goes to the invited address, from the configured sender, with the workspace in subject and text")
    void smtpSendsOneMessageToTheInvitedAddress() {
        MailSender sender = mock(MailSender.class);
        MailConfig.smtp(sender, "noreply@matriz.example.test", "https://matriz.example.test").send(INVITATION);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(ADDRESS);
        assertThat(sent.getValue().getFrom()).isEqualTo("noreply@matriz.example.test");
        assertThat(sent.getValue().getSubject()).contains("Harbor Works");
        assertThat(sent.getValue().getText()).contains("Carla Demo").contains("Viewer")
                .contains("https://matriz.example.test");
        assertThat(sent.getValue().getCc()).isNull();
        assertThat(sent.getValue().getBcc()).isNull();
    }

    @Test
    @DisplayName("AC-INC17.9, R-01 · the subject of the message handed to the mail sender has no CR or LF, even when the workspace name has them")
    void subjectSentToTheMailSenderIsOneLine() {
        MailSender sender = mock(MailSender.class);
        MailConfig.smtp(sender, "noreply@matriz.example.test", "").send(new InvitationMailer.Invitation(ID, ADDRESS,
                "Harbor\r\nBcc: x@y", "Carla Demo", "Viewer"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).doesNotContain("\r").doesNotContain("\n").contains("Harbor");
        assertThat(sent.getValue().getBcc()).isNull();
        assertThat(sent.getValue().getTo()).containsExactly(ADDRESS);
    }

    @Test
    @DisplayName("AC-INC17.8, QJ, C2 · a mail error becomes InvitationNotSentException; the log line names the invitation id and the error type, never the address")
    void mailErrorBecomesNotSent() {
        MailSender sender = mock(MailSender.class);
        doThrow(new MailSendException("refused for " + ADDRESS)).when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> MailConfig.smtp(sender, "noreply@matriz.example.test", "").send(INVITATION))
                .isInstanceOf(InvitationNotSentException.class);
        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0)).contains(ID.toString()).doesNotContain(ADDRESS);
    }
}
