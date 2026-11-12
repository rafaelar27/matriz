package com.matriz.tasks;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.projects.ProjectDirectory;
import com.matriz.shared.ApiException;
import com.matriz.workspaces.MemberDirectory;

/**
 * Task rules (CMP-05). Identity always comes from the session (the caller's account id); permission decisions are
 * made by {@link AccessPolicy}; the order of checks follows openapi.yaml.
 */
@Service
class TaskService {

    private static final String PROJECT_NOT_FOUND = "The project was not found.";
    private static final String TASK_NOT_FOUND = "The task was not found.";

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

    /** createTask (US-10; FR-09, FR-10, FR-11, FR-12). */
    @Transactional
    Task create(UUID callerAccountId, UUID projectId, String title, String description, String ownerMemberId,
                LocalDate dueDate) {
        UUID workspaceId = projects.workspaceOf(projectId).orElseThrow(() -> ApiException.notFound(PROJECT_NOT_FOUND));
        Actor caller = policy.requireWorkspaceAccess(members.actorIn(callerAccountId, workspaceId), PROJECT_NOT_FOUND);
        policy.checkCreateTask(caller, projects.isProjectMember(projectId, caller.memberId()));

        if (title == null || title.isEmpty()) {
            throw ApiException.validation("The task could not be created.", "title", "A title is required.");
        }
        if (ownerMemberId == null || ownerMemberId.isEmpty()) {
            throw ApiException.validation("The task could not be created.", "owner_member_id", "An owner is required.");
        }
        UUID owner = parseId(ownerMemberId);
        if (owner == null || !projects.isProjectMember(projectId, owner)) {
            throw ApiException.validation("The task could not be created.", "owner_member_id",
                    "The owner must be a member of this project.");
        }
        policy.checkAssignOwner(caller, owner);

        return tasks.save(new Task(projectId, title, description, owner, dueDate, caller.memberId()));
    }

    /** updateTaskStatus (US-11; FR-12). */
    @Transactional
    Task changeStatus(UUID callerAccountId, UUID taskId, String status) {
        Task task = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound(TASK_NOT_FOUND));
        UUID workspaceId = projects.workspaceOf(task.getProjectId())
                .orElseThrow(() -> ApiException.notFound(TASK_NOT_FOUND));
        Actor caller = policy.requireWorkspaceAccess(members.actorIn(callerAccountId, workspaceId), TASK_NOT_FOUND);
        policy.checkChangeStatus(caller, task.getOwnerMemberId());

        if (status == null) {
            throw ApiException.validation("The status could not be changed.", "status", "A status is required.");
        }
        TaskStatus newStatus = TaskStatus.fromLabel(status).orElseThrow(() -> ApiException.validation(
                "The status could not be changed.", "status", "The status must be one of To do, In progress, Done."));
        task.changeStatus(newStatus);
        return task;
    }

    static UUID parseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
