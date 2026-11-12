package com.matriz.tasks;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;
import com.matriz.workspaces.MemberDirectory.WorkspaceMember;

/** listEligibleAssignees, listTaskAssignees, assignTask, unassignTask (openapi.yaml 0.5.0-draft; FR-22, US-29). */
@RestController
@RequestMapping("/projects/{projectId}/tasks/{taskId}")
class AssignmentController {

    private final AssignmentService service;

    AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/eligible-assignees")
    List<WorkspaceMember> eligible(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                                   @PathVariable String taskId) {
        return service.eligible(caller.accountId(), id(projectId), id(taskId));
    }

    @GetMapping("/assignees")
    List<AssignmentService.AssigneeView> list(@AuthenticationPrincipal SignedInAccount caller,
                                              @PathVariable String projectId, @PathVariable String taskId) {
        return service.list(caller.accountId(), id(projectId), id(taskId));
    }

    @PostMapping("/assignees")
    ResponseEntity<List<AssignmentService.AssigneeView>> assign(@AuthenticationPrincipal SignedInAccount caller,
                                                                @PathVariable String projectId,
                                                                @PathVariable String taskId,
                                                                @RequestBody AssignRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.assign(caller.accountId(), id(projectId), id(taskId), body.memberIds()));
    }

    @DeleteMapping("/assignees/{memberId}")
    ResponseEntity<Void> unassign(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                                  @PathVariable String taskId, @PathVariable String memberId) {
        UUID member = Ids.parse(memberId)
                .orElseThrow(() -> ApiException.notFound("This member is not assigned to the task."));
        service.unassign(caller.accountId(), id(projectId), id(taskId), member);
        return ResponseEntity.noContent().build();
    }

    private static UUID id(String value) {
        return Ids.parse(value).orElseThrow(() -> ApiException.notFound(TaskService.TASK_NOT_FOUND));
    }

    record AssignRequest(List<String> memberIds) {
    }
}
