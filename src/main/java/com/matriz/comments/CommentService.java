package com.matriz.comments;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.projects.ProjectDirectory;
import com.matriz.shared.ApiException;
import com.matriz.tasks.TaskDirectory;
import com.matriz.workspaces.MemberDirectory;

/**
 * Task comments (CMP-07; FR-16, US-15, BR-13, EC-08). Access to the project is checked like every other task
 * operation (404 for other workspaces, API-A3), the task must belong to the project, and permissions are decided by
 * {@link AccessPolicy}. The author and the time are always set here.
 */
@Service
class CommentService {

    static final int BODY_MAX = 2000;
    static final String TASK_NOT_FOUND = "The task was not found.";
    private static final String NOT_POSTED = "The comment could not be posted.";

    private final CommentRepository comments;
    private final TaskDirectory tasks;
    private final ProjectDirectory projects;
    private final MemberDirectory members;
    private final AccessPolicy policy;

    CommentService(CommentRepository comments, TaskDirectory tasks, ProjectDirectory projects,
                   MemberDirectory members, AccessPolicy policy) {
        this.comments = comments;
        this.tasks = tasks;
        this.projects = projects;
        this.members = members;
        this.policy = policy;
    }

    /** listTaskComments: any active member of the workspace, Viewers included. */
    @Transactional(readOnly = true)
    List<CommentView> list(UUID callerAccountId, UUID projectId, UUID taskId) {
        access(callerAccountId, projectId, taskId);
        return views(comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(taskId));
    }

    /** addTaskComment: trimmed body of 1 to 2000 characters (decided 2026-11-12); spaces only is refused (BR-13). */
    @Transactional
    CommentView add(UUID callerAccountId, UUID projectId, UUID taskId, String body) {
        Actor caller = access(callerAccountId, projectId, taskId);
        policy.checkComment(caller, projects.isProjectMember(projectId, caller.memberId()));

        String text = body(body);
        Comment saved = comments.save(new Comment(taskId, caller.memberId(), text, Instant.now()));
        return views(List.of(saved)).get(0);
    }

    /** Trimmed, 1 to 2000 characters; a null or spaces-only body is refused (BR-13, FR-16, EC-08). */
    static String body(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw ApiException.validation(NOT_POSTED, "body", "A comment cannot be empty.");
        }
        if (text.codePointCount(0, text.length()) > BODY_MAX) {
            throw ApiException.validation(NOT_POSTED, "body", "A comment can be at most 2000 characters long.");
        }
        return text;
    }

    private Actor access(UUID callerAccountId, UUID projectId, UUID taskId) {
        UUID workspaceId = projects.workspaceOf(projectId).orElseThrow(() -> ApiException.notFound(TASK_NOT_FOUND));
        Actor caller = policy.requireWorkspaceAccess(members.actorIn(callerAccountId, workspaceId), TASK_NOT_FOUND);
        if (!tasks.belongsTo(taskId, projectId)) {
            throw ApiException.notFound(TASK_NOT_FOUND);
        }
        return caller;
    }

    /** An author removed from the workspace has no member id any more (V7) and is shown as "Former member" (QR). */
    private List<CommentView> views(List<Comment> list) {
        Map<UUID, String> names = members.namesOf(list.stream().map(Comment::getAuthorMemberId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return list.stream().map(c -> new CommentView(c.getId(), c.getTaskId(), c.getAuthorMemberId(),
                c.getAuthorMemberId() == null ? "Former member" : names.getOrDefault(c.getAuthorMemberId(), ""),
                c.getBody(), c.getCreatedAt())).toList();
    }

    /** A comment as returned by the API (openapi.yaml: Comment): no email or other account data. */
    record CommentView(UUID commentId, UUID taskId, UUID authorMemberId, String authorName, String body,
                       Instant createdAt) {
    }
}
