package com.matriz.accesscontrol;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.matriz.shared.ApiException;

/**
 * Every permission rule in one place (CMP-03, SP-03). Feature modules call it before every protected action and
 * pass the facts it needs. It reads no data itself.
 *
 * <p>Rules marked <b>PROVISIONAL</b> apply the most restrictive reading of a pending product decision, as approved
 * by the project decision-maker on 2026-11-12. Each must be revisited when its PD is decided.
 */
@Component
public class AccessPolicy {

    static final String VIEWER_READ_ONLY = "Viewers have read-only access.";

    /**
     * Workspace isolation and deactivation (NFR-01, SP-02, SP-04, BR-02). No membership in the resource's workspace
     * gives 404, exactly like a resource that does not exist (API-A3). A deactivated membership gives 403.
     */
    public Actor requireWorkspaceAccess(Optional<Actor> membership, String notFoundMessage) {
        Actor actor = membership.orElseThrow(() -> ApiException.notFound(notFoundMessage));
        if (!actor.active()) {
            throw ApiException.forbidden("Your membership in this workspace is deactivated.");
        }
        return actor;
    }

    /**
     * createProject, updateProject, deleteProject: any active Project Manager or Workspace Administrator of the
     * project's workspace (FR-06, US-06, US-24, US-27; "any Project Manager" decided 2026-11-12; Workspace
     * Administrators added 2026-11-19, PD-03 in part, so that the creator of a new workspace can manage its
     * projects). Viewing needs only workspace access (FR-08).
     */
    public void checkManageProject(Actor actor) {
        if (actor.role() == Role.VIEWER) {
            throw ApiException.forbidden(VIEWER_READ_ONLY);
        }
        if (actor.role() != Role.PROJECT_MANAGER && actor.role() != Role.WORKSPACE_ADMINISTRATOR) {
            throw ApiException.forbidden("Only Project Managers and Workspace Administrators can create, edit or delete projects.");
        }
    }

    /** createTask: who may create a task in a project (FR-09, BR-12, FR-05, AC-19.2). */
    public void checkCreateTask(Actor actor, boolean actorIsProjectMember) {
        if (actor.role() == Role.VIEWER) {
            throw ApiException.forbidden(VIEWER_READ_ONLY);
        }
        if (actor.role() == Role.WORKSPACE_ADMINISTRATOR) {
            // PROVISIONAL (PD-03): FR-09 names Project Managers and Team Members only.
            throw ApiException.forbidden("Workspace Administrators cannot create tasks (pending decision PD-03).");
        }
        if (!actorIsProjectMember) {
            // PROVISIONAL (PD-20): the creator must take part in the project.
            throw ApiException.forbidden("Only members of this project can create tasks in it (pending decision PD-20).");
        }
    }

    /**
     * createTask: who may be given as the owner, after the owner was checked to be a project member (FR-10, BR-09).
     */
    public void checkAssignOwner(Actor actor, UUID ownerMemberId) {
        if (actor.role() == Role.TEAM_MEMBER && !actor.memberId().equals(ownerMemberId)) {
            // PROVISIONAL (PD-01): Team Members may only assign tasks to themselves.
            throw ApiException.forbidden("Team Members can only create tasks they own (pending decision PD-01).");
        }
    }

    /** updateTask: who may change a task's status (FR-12, AC-11.1, AC-11.3, FR-05). */
    public void checkChangeStatus(Actor actor, UUID taskOwnerMemberId) {
        if (actor.role() == Role.VIEWER) {
            // PROVISIONAL (PD-17): refused even when the Viewer owns the task.
            throw ApiException.forbidden(VIEWER_READ_ONLY);
        }
        if (actor.role() == Role.WORKSPACE_ADMINISTRATOR) {
            // PROVISIONAL (PD-03): openapi.yaml allows owners who are Team Members or Project Managers.
            throw ApiException.forbidden("Workspace Administrators cannot change task status (pending decision PD-03).");
        }
        if (!actor.memberId().equals(taskOwnerMemberId)) {
            // PROVISIONAL (PD-01): only the task's owner, including for Project Managers.
            throw ApiException.forbidden("Only the task's owner can change its status (pending decision PD-01).");
        }
    }

    /**
     * updateTask (US-25, AC-10.4, FR-09, FR-10, FR-12): checks every field group that was sent, before any value is
     * validated, so a refused request changes nothing. PROVISIONAL rules approved 2026-11-12 (PD-01, PD-03, PD-17).
     *
     * @param changesContent title, description or due date was sent
     * @param changesOwner   owner_member_id was sent
     * @param changesStatus  status was sent
     */
    public void checkEditTask(Actor actor, UUID taskOwnerMemberId, boolean changesContent, boolean changesOwner,
                              boolean changesStatus) {
        requireTaskWriter(actor, "edit");
        boolean owner = actor.memberId().equals(taskOwnerMemberId);
        if (changesContent && actor.role() != Role.PROJECT_MANAGER && !owner) {
            // PROVISIONAL (PD-01): Team Members edit only the tasks they own.
            throw ApiException.forbidden("Team Members can only edit tasks they own (pending decision PD-01).");
        }
        if (changesOwner && actor.role() != Role.PROJECT_MANAGER) {
            // PROVISIONAL (PD-01): only Project Managers reassign tasks (AC-10.4).
            throw ApiException.forbidden("Only Project Managers can change a task's owner (pending decision PD-01).");
        }
        if (changesStatus) {
            checkChangeStatus(actor, taskOwnerMemberId);
        }
    }

    /** deleteTask (US-26, BR-12, AC-26.2): only the member who created the task, never a Viewer (FR-05). */
    public void checkDeleteTask(Actor actor, UUID taskCreatorMemberId) {
        requireTaskWriter(actor, "delete");
        if (!actor.memberId().equals(taskCreatorMemberId)) {
            throw ApiException.forbidden("Only the member who created a task can delete it.");
        }
    }

    /**
     * assignTask, unassignTask (FR-22, US-29): Project Managers of the workspace only. PROVISIONAL until PD-01 is
     * decided, the same rule as changing a task's owner (AC-10.4); approved 2026-11-12.
     */
    public void checkAssign(Actor actor) {
        requireTaskWriter(actor, "assign");
        if (actor.role() != Role.PROJECT_MANAGER) {
            throw ApiException.forbidden("Only Project Managers can assign or unassign members (pending decision PD-01).");
        }
    }

    /**
     * addTaskComment (FR-16, US-15): Project Managers and Team Members who are members of the task's project.
     * PROVISIONAL rules approved 2026-11-12: Viewers refused (FR-05, PD-17), Workspace Administrators refused
     * (PD-03), project membership required as for task creation (PD-20).
     */
    public void checkComment(Actor actor, boolean actorIsProjectMember) {
        requireTaskWriter(actor, "comment on");
        if (!actorIsProjectMember) {
            throw ApiException.forbidden("Only members of this project can comment on its tasks.");
        }
    }

    /** Viewers are read-only (FR-05); Workspace Administrators only read tasks (PROVISIONAL, PD-03). */
    private static void requireTaskWriter(Actor actor, String action) {
        if (actor.role() == Role.VIEWER) {
            throw ApiException.forbidden(VIEWER_READ_ONLY);
        }
        if (actor.role() == Role.WORKSPACE_ADMINISTRATOR) {
            throw ApiException.forbidden("Workspace Administrators cannot " + action
                    + " tasks (pending decision PD-03).");
        }
    }
}
