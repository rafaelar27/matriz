package com.matriz.tasks;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A task as returned by the API (openapi.yaml: Task). The status is always the API value, such as "To do". */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TaskView(UUID taskId, UUID projectId, String title, String description, UUID ownerMemberId,
                String ownerName, LocalDate dueDate, String status, UUID createdByMemberId) {

    static TaskView of(Task t, String ownerName) {
        return new TaskView(t.getId(), t.getProjectId(), t.getTitle(), t.getDescription(), t.getOwnerMemberId(),
                ownerName, t.getDueDate(), t.getStatus().label(), t.getCreatedByMemberId());
    }
}
