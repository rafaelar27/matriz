package com.matriz.projects;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Editable project fields as sent by the client (FR-06). Records which fields were present, so that a missing field
 * ("leave unchanged" / "use the default") can be told apart from an explicit {@code null} ("clear it").
 * Only these JSON fields are accepted; any other field is refused with 422 (openapi.yaml, additionalProperties false).
 */
class ProjectFields {

    private final Set<String> present = new HashSet<>();
    private String name;
    private String description;
    private String ownerMemberId;
    private LocalDate startDate;
    private LocalDate dueDate;

    boolean has(String field) {
        return present.contains(field);
    }

    String name() {
        return name;
    }

    public void setName(String name) {
        present.add("name");
        this.name = name;
    }

    String description() {
        return description;
    }

    public void setDescription(String description) {
        present.add("description");
        this.description = description;
    }

    String ownerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(String ownerMemberId) {
        present.add("owner_member_id");
        this.ownerMemberId = ownerMemberId;
    }

    LocalDate startDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        present.add("start_date");
        this.startDate = startDate;
    }

    LocalDate dueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        present.add("due_date");
        this.dueDate = dueDate;
    }

    /** Body of createProject: the editable fields plus the workspace. */
    static class Create extends ProjectFields {

        private String workspaceId;

        String workspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }
    }
}
