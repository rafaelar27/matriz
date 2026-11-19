package com.matriz.workspaces;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.Role;
import com.matriz.shared.ApiException;

/**
 * Workspace rules (CMP-02; FR-02, BR-01). Any signed-in account can create a workspace (decided in the plan for
 * INC-12). The account always comes from the session; the creator becomes its first member, a Workspace
 * Administrator, in the same transaction, so a workspace never exists without one (SP-07, R-02, NFR-03).
 */
@Service
class WorkspaceService {

    static final int NAME_MIN = 3;
    static final int NAME_MAX = 100;
    private static final String NOT_SAVED = "The workspace could not be saved.";

    private final WorkspaceRepository workspaces;
    private final MemberRepository members;

    WorkspaceService(WorkspaceRepository workspaces, MemberRepository members) {
        this.workspaces = workspaces;
        this.members = members;
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

    /** Trimmed, 3 to 100 characters; a null name is refused like an empty one (same rule as project names). */
    static String name(String value) {
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
