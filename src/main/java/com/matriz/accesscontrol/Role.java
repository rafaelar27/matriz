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
}
