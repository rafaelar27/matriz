package com.matriz.email;

/**
 * Subject and body of the invitation email (decision QI, 2026-11-19): the workspace name, the inviter's name and the
 * role, and how to accept. Plain text only, so names are never interpreted as markup.
 */
final class InvitationEmailText {

    private InvitationEmailText() {
    }

    /**
     * One line: control characters (CR, LF, tab and others) in the workspace name become spaces, so a name can never
     * add a header to the email (review R-01, header injection).
     */
    static String subject(InvitationMailer.Invitation invitation) {
        String name = invitation.workspaceName().replaceAll("\\p{Cntrl}+", " ").strip();
        return "You are invited to " + name + " on Matriz";
    }

    /** @param publicUrl the address of Matriz (MATRIZ_PUBLIC_URL); the link is left out when it is blank */
    static String body(InvitationMailer.Invitation invitation, String publicUrl) {
        StringBuilder text = new StringBuilder()
                .append(invitation.inviterName()).append(" invited you to the workspace \"")
                .append(invitation.workspaceName()).append("\" on Matriz as ").append(invitation.role()).append(".\n\n")
                .append("To accept, sign in to Matriz with this email address, or sign up with it if you have no account yet.");
        if (publicUrl != null && !publicUrl.isBlank()) {
            text.append("\n\n").append(publicUrl.strip());
        }
        return text.append("\n\nIf you did not expect this invitation, you can ignore this email.\n").toString();
    }
}
