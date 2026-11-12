package com.matriz.comments;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;

/**
 * listTaskComments and addTaskComment (openapi.yaml 0.6.0-draft; FR-16, US-15). The body accepts only {@code body};
 * a client-supplied author, date or any other field is refused with 422.
 */
@RestController
@RequestMapping("/projects/{projectId}/tasks/{taskId}/comments")
class CommentController {

    private final CommentService service;

    CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping
    List<CommentService.CommentView> list(@AuthenticationPrincipal SignedInAccount caller,
                                          @PathVariable String projectId, @PathVariable String taskId) {
        return service.list(caller.accountId(), id(projectId), id(taskId));
    }

    @PostMapping
    ResponseEntity<CommentService.CommentView> add(@AuthenticationPrincipal SignedInAccount caller,
                                                   @PathVariable String projectId, @PathVariable String taskId,
                                                   @RequestBody CommentRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.add(caller.accountId(), id(projectId), id(taskId), body.body()));
    }

    private static UUID id(String value) {
        return Ids.parse(value).orElseThrow(() -> ApiException.notFound(CommentService.TASK_NOT_FOUND));
    }

    record CommentRequest(String body) {
    }
}
