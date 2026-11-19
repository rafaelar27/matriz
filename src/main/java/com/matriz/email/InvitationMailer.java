package com.matriz.email;

import java.util.UUID;

/**
 * Public interface of CMP-13 for other modules: deliver one invitation email (FR-03, AC-03.1).
 *
 * <p>Implementations throw {@link InvitationNotSentException} when the email could not be handed to the mail
 * server. The caller then refuses the invitation and stores nothing (decision QJ, 2026-11-19).
 */
public interface InvitationMailer {

    void send(Invitation invitation);

    /**
     * The facts an invitation email needs. {@code to} is personal data: it is given to the mail server only and is
     * never logged (C2, NFR-02).
     *
     * @param invitationId  id of the stored invitation, safe to log
     * @param to            the invited address, lower case (BR-24)
     * @param workspaceName the workspace the person is invited to
     * @param inviterName   name of the Workspace Administrator who sent it
     * @param role          the role label, for example "Team Member"
     */
    record Invitation(UUID invitationId, String to, String workspaceName, String inviterName, String role) {
    }
}
