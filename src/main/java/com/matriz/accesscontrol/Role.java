package com.matriz.accesscontrol;

/** The four roles (FR-04, BR-03, AQ11 confirmed). Stored as these codes in member.role. */
public enum Role {
    WORKSPACE_ADMINISTRATOR("Workspace Administrator"),
    PROJECT_MANAGER("Project Manager"),
    TEAM_MEMBER("Team Member"),
    VIEWER("Viewer");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** The role's name as used in the documentation and the API. */
    public String label() {
        return label;
    }

    /** The role with exactly this label (case-sensitive, as in openapi.yaml); empty for anything else. */
    public static java.util.Optional<Role> fromLabel(String label) {
        for (Role role : values()) {
            if (role.label.equals(label)) {
                return java.util.Optional.of(role);
            }
        }
        return java.util.Optional.empty();
    }
}
