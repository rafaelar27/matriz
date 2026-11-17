package com.matriz.tasks;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.projects.ProjectDirectory;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;
import com.matriz.workspaces.MemberDirectory;

/**
 * Task rules (CMP-05; FR-09 to FR-13, FR-15). Identity always comes from the session (the caller's account id);
 * permission decisions are made by {@link AccessPolicy}. Every operation first checks that the caller is an active
 * member of the project's workspace (404 otherwise, API-A3), and a task is only reachable through its own project.
 */
@Service
class TaskService {

    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 150;
    static final int DESCRIPTION_MAX = 2000;
    static final String PROJECT_NOT_FOUND = "The project was not found.";
    static final String TASK_NOT_FOUND = "The task was not found.";
    private static final String NOT_CREATED = "The task could not be created.";
    private static final String NOT_SAVED = "The task could not be saved.";
    private static final String BAD_STATUS = "The status must be one of To do, In progress, Done.";
    private static final UUID NO_OWNER = new UUID(0, 0);

    private final TaskRepository tasks;
    private final ProjectDirectory projects;
    private final MemberDirectory members;
    private final AccessPolicy policy;

    TaskService(TaskRepository tasks, ProjectDirectory projects, MemberDirectory members, AccessPolicy policy) {
        this.tasks = tasks;
        this.projects = projects;
        this.members = members;
        this.policy = policy;
    }

    /** listTasks (US-12, US-14; FR-13, FR-15): any active member of the workspace, Viewers included. */
    @Transactional(readOnly = true)
    TaskPage list(UUID callerAccountId, UUID projectId, Optional<TaskStatus> status, Optional<UUID> owner, int page,
                  int size) {
        accessToProject(callerAccountId, projectId, PROJECT_NOT_FOUND);
        Set<TaskStatus> statuses = status.map(Set::of).orElse(Set.of(TaskStatus.values()));
        Page<Task> result = tasks.search(projectId, statuses, owner.isEmpty(), owner.orElse(NO_OWNER),
                PageRequest.of(page, size));

        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        Arrays.stream(TaskStatus.values()).forEach(s -> counts.put(s, 0L));
        for (Object[] row : tasks.countByStatus(projectId)) {
            counts.put((TaskStatus) row[0], ((Number) row[1]).longValue());
        }
        List<StatusCount> statusCounts = Arrays.stream(TaskStatus.values())
                .map(s -> new StatusCount(s.label(), counts.get(s))).toList();
        return new TaskPage(views(result.getContent()), page, size, result.getTotalElements(), statusCounts);
    }

    /** getTask: any active member of the workspace; the task must belong to the project. */
    @Transactional(readOnly = true)
    TaskView get(UUID callerAccountId, UUID projectId, UUID taskId) {
        accessToProject(callerAccountId, projectId, TASK_NOT_FOUND);
        return view(load(projectId, taskId));
    }

    /** createTask (US-10; FR-09, FR-10, FR-11, FR-12). Status defaults to To do (decided 2026-11-12). */
    @Transactional
    TaskView create(UUID callerAccountId, UUID projectId, TaskFields body) {
        Actor caller = accessToProject(callerAccountId, projectId, PROJECT_NOT_FOUND);
        policy.checkCreateTask(caller, projects.isProjectMember(projectId, caller.memberId()));

        Task task = new Task(projectId, caller.memberId());
        task.setTitle(title(body.has(TaskFields.TITLE) ? body.title() : null, NOT_CREATED));
        if (body.has(TaskFields.DESCRIPTION)) {
            task.setDescription(description(body.description(), NOT_CREATED));
        }
        if (body.has(TaskFields.STATUS)) {
            task.changeStatus(status(body.status(), NOT_CREATED));
        }
        UUID owner = owner(projectId, body.has(TaskFields.OWNER) ? body.ownerMemberId() : null, NOT_CREATED);
        policy.checkAssignOwner(caller, owner);
        task.setOwnerMemberId(owner);
        if (body.has(TaskFields.DUE_DATE)) {
            task.setDueDate(body.dueDate());
        }
        return view(tasks.save(task));
    }

    /** updateTask (US-25, AC-10.4, US-11). Permissions for every sent field are checked before any value. */
    @Transactional
    TaskView update(UUID callerAccountId, UUID projectId, UUID taskId, TaskFields body) {
        Actor caller = accessToProject(callerAccountId, projectId, TASK_NOT_FOUND);
        Task task = load(projectId, taskId);
        boolean content = body.has(TaskFields.TITLE) || body.has(TaskFields.DESCRIPTION)
                || body.has(TaskFields.DUE_DATE);
        policy.checkEditTask(caller, task.getOwnerMemberId(), content, body.has(TaskFields.OWNER),
                body.has(TaskFields.STATUS));

        // Validate everything first, then apply, so a refused request changes nothing.
        String title = body.has(TaskFields.TITLE) ? title(body.title(), NOT_SAVED) : task.getTitle();
        String description = body.has(TaskFields.DESCRIPTION) ? description(body.description(), NOT_SAVED)
                : task.getDescription();
        TaskStatus status = body.has(TaskFields.STATUS) ? status(body.status(), NOT_SAVED) : task.getStatus();
        UUID owner = body.has(TaskFields.OWNER) ? owner(projectId, body.ownerMemberId(), NOT_SAVED)
                : task.getOwnerMemberId();
        LocalDate dueDate = body.has(TaskFields.DUE_DATE) ? body.dueDate() : task.getDueDate();

        task.setTitle(title);
        task.setDescription(description);
        task.changeStatus(status);
        task.setOwnerMemberId(owner);
        task.setDueDate(dueDate);
        return view(task);
    }

    /**
     * deleteTask (US-26, BR-12). The database deletes the task's comments and notifications in the same transaction
     * (DA-05, ON DELETE CASCADE in migration V1), so no dependent record remains.
     */
    @Transactional
    void delete(UUID callerAccountId, UUID projectId, UUID taskId) {
        Actor caller = accessToProject(callerAccountId, projectId, TASK_NOT_FOUND);
        Task task = load(projectId, taskId);
        policy.checkDeleteTask(caller, task.getCreatedByMemberId());
        tasks.delete(task);
        tasks.flush();
    }

    // --- Access -----------------------------------------------------------------------------------------------------

    /** The caller as an active member of the project's workspace; 404 if the project is unknown or elsewhere. */
    Actor accessToProject(UUID callerAccountId, UUID projectId, String notFoundMessage) {
        UUID workspaceId = projects.workspaceOf(projectId).orElseThrow(() -> ApiException.notFound(notFoundMessage));
        return policy.requireWorkspaceAccess(members.actorIn(callerAccountId, workspaceId), notFoundMessage);
    }

    /** The task, only if it belongs to this project: a task id used with another project's id gives 404. */
    Task load(UUID projectId, UUID taskId) {
        return tasks.findById(taskId).filter(t -> t.getProjectId().equals(projectId))
                .orElseThrow(() -> ApiException.notFound(TASK_NOT_FOUND));
    }

    // --- Validation (decided 2026-11-12: PD-04 lengths, PD-21 spaces-only titles) --------------------------------

    /** Trimmed, 3 to 150 characters; a missing, null or spaces-only title is refused. */
    static String title(String value, String message) {
        String title = value == null ? "" : value.trim();
        if (title.isEmpty()) {
            throw ApiException.validation(message, TaskFields.TITLE, "A title is required.");
        }
        int length = title.codePointCount(0, title.length());
        if (length < TITLE_MIN || length > TITLE_MAX) {
            throw ApiException.validation(message, TaskFields.TITLE, "The title must be 3 to 150 characters long.");
        }
        return title;
    }

    /** At most 2000 characters; empty or null clears it. */
    static String description(String value, String message) {
        if (value != null && value.codePointCount(0, value.length()) > DESCRIPTION_MAX) {
            throw ApiException.validation(message, TaskFields.DESCRIPTION,
                    "The description can be at most 2000 characters long.");
        }
        return value == null || value.isEmpty() ? null : value;
    }

    /** Exactly one of the three API values (AC-11.2); anything else, including null, is refused. */
    static TaskStatus status(String value, String message) {
        return TaskStatus.fromLabel(value)
                .orElseThrow(() -> ApiException.validation(message, TaskFields.STATUS, BAD_STATUS));
    }

    /** A member of this project (FR-10, BR-09, AC-10.3); members of other workspaces never are (NFR-01). */
    private UUID owner(UUID projectId, String value, String message) {
        if (value == null || value.isEmpty()) {
            throw ApiException.validation(message, TaskFields.OWNER, "An owner is required.");
        }
        return Ids.parse(value).filter(id -> projects.isProjectMember(projectId, id))
                .orElseThrow(() -> ApiException.validation(message, TaskFields.OWNER,
                        "The owner must be a member of this project."));
    }

    // --- Responses --------------------------------------------------------------------------------------------------

    private TaskView view(Task task) {
        return views(List.of(task)).get(0);
    }

    private List<TaskView> views(List<Task> list) {
        Map<UUID, String> names = members.namesOf(list.stream().map(Task::getOwnerMemberId)
                .collect(Collectors.toSet()));
        return list.stream().map(t -> TaskView.of(t, names.get(t.getOwnerMemberId()))).toList();
    }

    record TaskPage(List<TaskView> items, int page, int size, long total, List<StatusCount> statusCounts) {
    }

    record StatusCount(String status, long count) {
    }
}
