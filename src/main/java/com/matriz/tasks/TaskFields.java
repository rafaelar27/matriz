package com.matriz.tasks;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Task fields as sent by the client (openapi.yaml: TaskCreateRequest, TaskUpdateRequest). Records which fields were
 * present, so that a missing field ("leave unchanged" / "use the default") can be told apart from an explicit
 * {@code null} ("clear it"). Only these JSON fields are accepted; any other field, such as
 * {@code created_by_member_id}, is refused with 422 (additionalProperties false).
 */
class TaskFields {

    static final String TITLE = "title";
    static final String DESCRIPTION = "description";
    static final String OWNER = "owner_member_id";
    static final String DUE_DATE = "due_date";
    static final String STATUS = "status";

    private final Set<String> present = new HashSet<>();
    private String title;
    private String description;
    private String ownerMemberId;
    private LocalDate dueDate;
    private String status;

    boolean has(String field) {
        return present.contains(field);
    }

    String title() {
        return title;
    }

    public void setTitle(String title) {
        present.add(TITLE);
        this.title = title;
    }

    String description() {
        return description;
    }

    public void setDescription(String description) {
        present.add(DESCRIPTION);
        this.description = description;
    }

    String ownerMemberId() {
        return ownerMemberId;
    }

    public void setOwnerMemberId(String ownerMemberId) {
        present.add(OWNER);
        this.ownerMemberId = ownerMemberId;
    }

    LocalDate dueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        present.add(DUE_DATE);
        this.dueDate = dueDate;
    }

    String status() {
        return status;
    }

    public void setStatus(String status) {
        present.add(STATUS);
        this.status = status;
    }
}
