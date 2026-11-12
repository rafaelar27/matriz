package com.matriz.projects;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A project as returned by the API (openapi.yaml: Project; ProjectSummary omits nothing but the description). */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ProjectView(UUID projectId, UUID workspaceId, String workspaceName, String name, String description,
                   UUID ownerMemberId, String ownerName, LocalDate startDate, LocalDate dueDate, boolean isArchived) {

    /** The list form (ProjectSummary): without the description. */
    ProjectView summary() {
        return new ProjectView(projectId, workspaceId, workspaceName, name, null, ownerMemberId, ownerName,
                startDate, dueDate, isArchived);
    }
}
