package com.matriz.workspaces;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.Actor;
import com.matriz.accesscontrol.Role;
import com.matriz.shared.ApiException;

/**
 * Workspace rules (CMP-02; FR-02, BR-01). Any signed-in account can create a workspace (decided in the plan for
 * INC-14). The account always comes from the session; the creator becomes its first member, a Workspace
 * Administrator, in the same transaction, so a workspace never exists without one (SP-07, R-02, NFR-03).
 */
@Service
class WorkspaceService {

    static final int NAME_MIN = 3;
    static final int NAME_MAX = 100;
    private static final String NOT_SAVED = "The workspace could not be saved.";

    private final WorkspaceRepository workspaces;
    private final MemberRepository members;
    private final AdminGuard guard;

    WorkspaceService(WorkspaceRepository workspaces, MemberRepository members, AdminGuard guard) {
        this.workspaces = workspaces;
        this.members = members;
        this.guard = guard;
    }

    /** createWorkspace (US-02). */
    @Transactional
    WorkspaceView create(UUID callerAccountId, WorkspaceFields body) {
        if (!body.has("name")) {
            throw ApiException.validation(NOT_SAVED, "name", "A name is required.");
        }
        Workspace workspace = workspaces.save(new Workspace(name(body.name())));
        Member first = members.save(new Member(workspace, callerAccountId, Role.WORKSPACE_ADMINISTRATOR));
        return new WorkspaceView(workspace.getId(), workspace.getName(), first.getId(), first.getRole().label());
    }

    /** updateWorkspace (FR-23, INC-18): any active Workspace Administrator; the BR-01 name rule. */
    @Transactional
    WorkspaceSummary rename(Actor actor, UUID workspaceId, WorkspaceFields body) {
        guard.lockAndRecheck(workspaceId, actor); // S-01: still an administrator inside this transaction
        if (!body.has("name")) {
            throw ApiException.validation(NOT_SAVED, "name", "A name is required.");
        }
        Workspace workspace = workspaces.findById(workspaceId).orElseThrow();
        workspace.rename(name(body.name()));
        return new WorkspaceSummary(workspace.getId(), workspace.getName());
    }

    /**
     * deleteWorkspace (FR-24, INC-19): soft delete after the administrator typed the current name, compared after
     * trimming and with letter case (decision QN). From then on every operation on the workspace gives 404.
     */
    @Transactional
    void delete(Actor actor, UUID workspaceId, WorkspaceDeleteFields body) {
        guard.lockAndRecheck(workspaceId, actor); // S-01
        Workspace workspace = workspaces.findById(workspaceId).orElseThrow();
        String typed = body.confirmName() == null ? "" : body.confirmName().trim();
        if (!typed.equals(workspace.getName())) {
            throw ApiException.validation("The workspace was not deleted.", "confirm_name",
                    "Type the workspace's name exactly as shown.");
        }
        workspace.markDeleted();
    }

    /** openapi.yaml WorkspaceSummary. */
    record WorkspaceSummary(UUID workspaceId, String name) {
    }

    /**
     * Trimmed, 3 to 100 characters; a null name is refused like an empty one (same rule as project names). A name
     * with a NUL character (U+0000) is refused too, because the database cannot store it (decided 2026-11-19).
     */
    static String name(String value) {
        if (value != null && value.indexOf('\u0000') >= 0) {
            throw ApiException.validation(NOT_SAVED, "name", "The name cannot contain a NUL character.");
        }
        String name = value == null ? "" : value.trim();
        int length = name.codePointCount(0, name.length());
        if (length < NAME_MIN || length > NAME_MAX) {
            throw ApiException.validation(NOT_SAVED, "name", "The name must be 3 to 100 characters long.");
        }
        return name;
    }

    /** The new workspace and the creator's membership. */
    record WorkspaceView(UUID workspaceId, String name, UUID memberId, String role) {
    }
}
