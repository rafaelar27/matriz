package com.matriz.tasks;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;

/**
 * createTask and updateTaskStatus (openapi.yaml). Request bodies accept only the fields the contract defines; any
 * other field, such as {@code status} or {@code created_by_member_id}, is refused with 422.
 */
@RestController
class TaskController {

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/tasks")
    ResponseEntity<TaskResponse> create(@AuthenticationPrincipal SignedInAccount caller,
                                        @PathVariable String projectId, @RequestBody TaskCreateRequest body) {
        UUID project = idOrNotFound(projectId, "The project was not found.");
        Task task = service.create(caller.accountId(), project, body.title(), body.description(),
                body.ownerMemberId(), body.dueDate());
        return ResponseEntity.created(URI.create("/tasks/" + task.getId())).body(TaskResponse.of(task));
    }

    @PatchMapping("/tasks/{taskId}")
    TaskResponse changeStatus(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String taskId,
                              @RequestBody TaskStatusUpdateRequest body) {
        UUID task = idOrNotFound(taskId, "The task was not found.");
        return TaskResponse.of(service.changeStatus(caller.accountId(), task, body.status()));
    }

    /** An identifier that is not a valid id cannot exist, so it is reported like any unknown id (404). */
    private static UUID idOrNotFound(String value, String message) {
        UUID id = TaskService.parseId(value);
        if (id == null) {
            throw ApiException.notFound(message);
        }
        return id;
    }

    record TaskCreateRequest(String title, String description, String ownerMemberId, LocalDate dueDate) {
    }

    record TaskStatusUpdateRequest(String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TaskResponse(UUID taskId, UUID projectId, String title, String description, UUID ownerMemberId,
                        LocalDate dueDate, String status, UUID createdByMemberId) {

        static TaskResponse of(Task t) {
            return new TaskResponse(t.getId(), t.getProjectId(), t.getTitle(), t.getDescription(),
                    t.getOwnerMemberId(), t.getDueDate(), t.getStatus().label(), t.getCreatedByMemberId());
        }
    }
}
