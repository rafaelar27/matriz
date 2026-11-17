package com.matriz.tasks;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;

/**
 * listTasks, createTask, getTask, updateTask and deleteTask (openapi.yaml 0.7.0-draft). A task's status changes through updateTask. Request
 * bodies accept only the fields the contract defines; any other field, such as {@code created_by_member_id}, is
 * refused with 422.
 */
@RestController
class TaskController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String NOT_LISTED = "The tasks could not be listed.";

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/tasks")
    TaskService.TaskPage list(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                              @RequestParam(required = false) String status,
                              @RequestParam(name = "owner_member_id", required = false) String ownerMemberId,
                              @RequestParam(required = false) String page,
                              @RequestParam(required = false) String size) {
        UUID project = id(projectId, TaskService.PROJECT_NOT_FOUND);
        Optional<TaskStatus> statusFilter = Optional.empty();
        if (status != null && !status.isEmpty()) {
            statusFilter = Optional.of(TaskService.status(status, NOT_LISTED));
        }
        Optional<UUID> ownerFilter = Optional.empty();
        if (ownerMemberId != null && !ownerMemberId.isEmpty()) {
            ownerFilter = Optional.of(Ids.parse(ownerMemberId).orElseThrow(() -> ApiException.validation(
                    NOT_LISTED, "owner_member_id", "The owner id is not valid.")));
        }
        int p = intParam("page", page, 0, 0, Integer.MAX_VALUE);
        int s = intParam("size", size, DEFAULT_SIZE, 1, MAX_SIZE);
        return service.list(caller.accountId(), project, statusFilter, ownerFilter, p, s);
    }

    @PostMapping("/projects/{projectId}/tasks")
    ResponseEntity<TaskView> create(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                                    @RequestBody TaskFields body) {
        TaskView task = service.create(caller.accountId(), id(projectId, TaskService.PROJECT_NOT_FOUND), body);
        return ResponseEntity.created(URI.create("/tasks/" + task.taskId())).body(task);
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    TaskView get(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                 @PathVariable String taskId) {
        return service.get(caller.accountId(), id(projectId, TaskService.TASK_NOT_FOUND),
                id(taskId, TaskService.TASK_NOT_FOUND));
    }

    @PatchMapping("/projects/{projectId}/tasks/{taskId}")
    TaskView update(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                    @PathVariable String taskId, @RequestBody TaskFields body) {
        return service.update(caller.accountId(), id(projectId, TaskService.TASK_NOT_FOUND),
                id(taskId, TaskService.TASK_NOT_FOUND), body);
    }

    @DeleteMapping("/projects/{projectId}/tasks/{taskId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                                @PathVariable String taskId) {
        service.delete(caller.accountId(), id(projectId, TaskService.TASK_NOT_FOUND),
                id(taskId, TaskService.TASK_NOT_FOUND));
        return ResponseEntity.noContent().build();
    }

    /** An identifier that is not a valid id cannot exist, so it is reported like any unknown id (404). */
    private static UUID id(String value, String message) {
        return Ids.parse(value).orElseThrow(() -> ApiException.notFound(message));
    }

    /** Same range checks and messages as listProjects (openapi.yaml: page, size). */
    private static int intParam(String name, String value, int defaultValue, int min, int max) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            int n = Integer.parseInt(value);
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        String range = max == Integer.MAX_VALUE ? "a whole number of at least " + min
                : "a whole number from " + min + " to " + max;
        throw ApiException.validation(NOT_LISTED, name, "The " + name + " must be " + range + ".");
    }
}
