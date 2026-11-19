package com.matriz.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Subject and body of the invitation email as pure logic (INC-17; US-03; FR-03, AC-03.1; decision QI of 2026-11-19).
 * No Spring context, no database. Expected: the workspace name, the inviter's name and the role are in the text,
 * the text says how to accept, the link appears only when an address of Matriz is configured, and the text is plain
 * (names are not changed or interpreted as markup).
 */
class InvitationEmailTextTest {

    private static final InvitationMailer.Invitation INVITATION = new InvitationMailer.Invitation(
            UUID.fromString("a0000000-0000-0000-0000-0000000000e1"), "ana@example.com", "Harbor Works", "Carla Demo",
            "Team Member");

    @Test
    @DisplayName("AC-INC17.9, FR-03 · the subject names the workspace")
    void subjectNamesTheWorkspace() {
        assertThat(InvitationEmailText.subject(INVITATION)).contains("Harbor Works").contains("Matriz");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Harbor\r\nBcc: x@y", "Harbor\nBcc: x@y", "Harbor\rBcc: x@y", "Harbor\tWorks\u0000\u001b",
        "\r\nHarbor", "Harbor\r\n"})
    @DisplayName("AC-INC17.9, R-01, FR-03 · control characters (CR, LF, tab, NUL, ESC) in the workspace name never reach the subject: it is one line, with spaces instead")
    void subjectHasNoControlCharacters(String name) {
        InvitationMailer.Invitation tricky = new InvitationMailer.Invitation(UUID.randomUUID(), "ana@example.com",
                name, "Carla Demo", "Viewer");
        String subject = InvitationEmailText.subject(tricky);
        assertThat(subject).doesNotContain("\r").doesNotContain("\n").doesNotContain("\t").doesNotContain("\u0000")
                .doesNotContain("\u001b");
        assertThat(subject).startsWith("You are invited to ").endsWith(" on Matriz").contains("Harbor");
    }

    @Test
    @DisplayName("AC-INC17.9, R-01 · a CR LF in the workspace name becomes a space and the rest of the name stays in the subject")
    void subjectKeepsTheRestOfTheName() {
        InvitationMailer.Invitation tricky = new InvitationMailer.Invitation(UUID.randomUUID(), "ana@example.com",
                "Harbor\r\nBcc: x@y", "Carla Demo", "Viewer");
        assertThat(InvitationEmailText.subject(tricky)).isEqualTo("You are invited to Harbor Bcc: x@y on Matriz");
    }

    @Test
    @DisplayName("AC-INC17.9, FR-03, AC-03.1 · the body names the workspace, the inviter and the role, and says how to accept")
    void bodyHasTheFacts() {
        String body = InvitationEmailText.body(INVITATION, "https://matriz.example.test");
        assertThat(body).contains("Carla Demo").contains("Harbor Works").contains("Team Member");
        assertThat(body).containsIgnoringCase("sign in").containsIgnoringCase("sign up");
        assertThat(body).containsIgnoringCase("ignore this email");
    }

    @Test
    @DisplayName("AC-INC17.9 · the body contains the configured Matriz address, stripped of surrounding spaces")
    void bodyHasTheLink() {
        assertThat(InvitationEmailText.body(INVITATION, "  https://matriz.example.test  "))
                .contains("\n\nhttps://matriz.example.test\n");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("AC-INC17.9 · without a configured Matriz address the link is left out and the text is still complete")
    void bodyWithoutLink(String publicUrl) {
        String body = InvitationEmailText.body(INVITATION, publicUrl);
        assertThat(body).doesNotContain("http").contains("Harbor Works").contains("ignore this email");
    }

    @Test
    @DisplayName("AC-INC17.9, FR-03, QI · names are copied as plain text: markup in a workspace or inviter name is not changed")
    void namesAreNotInterpreted() {
        InvitationMailer.Invitation tricky = new InvitationMailer.Invitation(UUID.randomUUID(), "ana@example.com",
                "<b>Space</b> & \"Co\"", "<i>Carla</i>", "Viewer");
        assertThat(InvitationEmailText.subject(tricky)).contains("<b>Space</b> & \"Co\"");
        assertThat(InvitationEmailText.body(tricky, null)).contains("<i>Carla</i>").contains("<b>Space</b> & \"Co\"")
                .doesNotContain("&lt;").doesNotContain("&amp;");
    }
}
