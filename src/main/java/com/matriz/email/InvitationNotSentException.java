package com.matriz.email;

/** The invitation email could not be handed to the mail server (decision QJ: the invitation is not kept). */
public class InvitationNotSentException extends RuntimeException {

    InvitationNotSentException(Throwable cause) {
        super("The invitation email could not be sent.", cause);
    }
}
