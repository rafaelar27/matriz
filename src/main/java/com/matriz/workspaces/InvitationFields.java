package com.matriz.workspaces;

import java.util.HashSet;
import java.util.Set;

/**
 * Body of createInvitation (FR-03). Only {@code email} and {@code role} are accepted; any other field, such as
 * {@code invited_by_member_id}, {@code status} or {@code workspace_id}, is refused with 422 (openapi.yaml,
 * additionalProperties false). Records which fields were sent.
 */
class InvitationFields {

    private final Set<String> present = new HashSet<>();
    private String email;
    private String role;

    boolean has(String field) {
        return present.contains(field);
    }

    String email() {
        return email;
    }

    String role() {
        return role;
    }

    public void setEmail(String email) {
        present.add("email");
        this.email = email;
    }

    public void setRole(String role) {
        present.add("role");
        this.role = role;
    }
}
