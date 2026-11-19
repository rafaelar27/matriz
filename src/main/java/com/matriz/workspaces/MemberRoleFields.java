package com.matriz.workspaces;

/** Body of updateMemberRole (FR-04). Only {@code role} is accepted; any other field is refused with 422. */
class MemberRoleFields {

    private String role;

    String role() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
