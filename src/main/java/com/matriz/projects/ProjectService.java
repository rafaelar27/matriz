package com.matriz.projects;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;
import com.matriz.workspaces.MemberDirectory;

/**
 * Project rules (CMP-04; FR-05, FR-06, FR-08). Identity always comes from the session; permissions are decided by
 * {@link AccessPolicy}. A project in a workspace the caller is not an active member of is reported as not found.
 */
@Service
class ProjectService {

    static final int NAME_MIN = 3;
    static final int NAME_MAX = 100;
    static final int DESCRIPTION_MAX = 1000;
    static final String NOT_FOUND = "The project was not found.";
    private static final String NOT_SAVED = "The project could not be saved.";

    private final ProjectRepository projects;
    private final MemberDirectory members;
    private final AccessPolicy policy;

    ProjectService(ProjectRepository projects, MemberDirectory members, AccessPolicy policy) {
        this.projects = projects;
        this.members = members;
        this.policy = policy;
    }

    /** listProjects: projects of the caller's active workspaces, optionally filtered (FR-08, US-28). */
    @Transactional(readOnly = true)
    ProjectPage list(UUID callerAccountId, String q, Optional<UUID> workspaceFilter, int page, int size) {
        Set<UUID> visible = members.activeMembershipsOf(callerAccountId).stream()
                .map(m -> m.workspaceId())
                .filter(ws -> workspaceFilter.map(ws::equals).orElse(true))
                .collect(Collectors.toSet());
        if (visible.isEmpty()) {
            return new ProjectPage(List.of(), page, size, 0);
        }
        String text = q == null ? "" : q.trim().toLowerCase();
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Page<Project> result = projects.search(visible, pattern, PageRequest.of(page, size));
        return new ProjectPage(views(result.getContent()).stream().map(ProjectView::summary).toList(), page, size,
                result.getTotalElements());
    }

    /** getProject: any active member of the project's workspace. */
    @Transactional(readOnly = true)
    ProjectView get(UUID callerAccountId, UUID projectId) {
        Project project = load(projectId);
        policy.requireWorkspaceAccess(members.actorIn(callerAccountId, project.getWorkspaceId()), NOT_FOUND);
        return views(List.of(project)).get(0);
    }

    /** createProject (US-06). The caller becomes a project member (decided 2026-11-12). */
    @Transactional
    ProjectView create(UUID callerAccountId, ProjectFields.Create body) {
        if (body.workspaceId() == null || body.workspaceId().isEmpty()) {
            throw ApiException.validation(NOT_SAVED, "workspace_id", "A workspace is required.");
        }
        UUID workspaceId = Ids.parse(body.workspaceId())
                .orElseThrow(() -> ApiException.notFound("The workspace was not found."));
        Actor caller = policy.requireWorkspaceAccess(members.actorIn(callerAccountId, workspaceId),
                "The workspace was not found.");
        policy.checkCreateProject(caller);

        if (!body.has("name")) {
            throw ApiException.validation(NOT_SAVED, "name", "A name is required.");
        }
        Project project = new Project(workspaceId);
        apply(project, body);
        if (!body.has("owner_member_id")) {
            project.setOwnerMemberId(caller.memberId()); // FR-06: owner defaults to the creator
        }
        project = projects.save(project);
        projects.addProjectMember(project.getId(), caller.memberId());
        return views(List.of(project)).get(0);
    }

    /** updateProject (US-24, AC-24.1). */
    @Transactional
    ProjectView update(UUID callerAccountId, UUID projectId, ProjectFields body) {
        Project project = load(projectId);
        Actor caller = policy.requireWorkspaceAccess(members.actorIn(callerAccountId, project.getWorkspaceId()),
                NOT_FOUND);
        policy.checkManageProject(caller);
        apply(project, body);
        return views(List.of(project)).get(0);
    }

    /**
     * deleteProject (US-27). The database deletes the project's tasks, their comments and notifications, and its
     * project members in the same transaction (migration V2, DA-05), so no dependent record remains.
     */
    @Transactional
    void delete(UUID callerAccountId, UUID projectId) {
        Project project = load(projectId);
        Actor caller = policy.requireWorkspaceAccess(members.actorIn(callerAccountId, project.getWorkspaceId()),
                NOT_FOUND);
        policy.checkManageProject(caller);
        projects.delete(project);
        projects.flush();
    }

    private Project load(UUID projectId) {
        return projects.findById(projectId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    /** Validates and applies the fields that were sent. */
    private void apply(Project project, ProjectFields body) {
        if (body.has("name")) {
            project.setName(name(body.name()));
        }
        if (body.has("description")) {
            project.setDescription(description(body.description()));
        }
        if (body.has("owner_member_id")) {
            requireOwner(body.ownerMemberId());
            UUID owner = Ids.parse(body.ownerMemberId())
                    .filter(id -> members.isActiveMemberOf(id, project.getWorkspaceId()))
                    .orElseThrow(() -> ApiException.validation(NOT_SAVED, "owner_member_id",
                            "The owner must be an active member of this workspace."));
            project.setOwnerMemberId(owner);
        }
        if (body.has("start_date")) {
            project.setStartDate(body.startDate());
        }
        if (body.has("due_date")) {
            project.setDueDate(body.dueDate());
        }
        // Checked on the project as it is after the change, so sending one date can be refused (decided 2026-11-12,
        // FR-06). The exception rolls the transaction back, so nothing is saved.
        requireDateOrder(project.getStartDate(), project.getDueDate());
    }

    // --- Pure rules (FR-06, BR-21, BR-23), unit-tested without Spring ------------------------------------------

    /** Trimmed, 3 to 100 characters; a null name is refused like an empty one. */
    static String name(String value) {
        String name = value == null ? "" : value.trim();
        int length = name.codePointCount(0, name.length());
        if (length < NAME_MIN || length > NAME_MAX) {
            throw ApiException.validation(NOT_SAVED, "name", "The name must be 3 to 100 characters long.");
        }
        return name;
    }

    /** At most 1000 characters; empty or null clears it. */
    static String description(String value) {
        if (value != null && value.codePointCount(0, value.length()) > DESCRIPTION_MAX) {
            throw ApiException.validation(NOT_SAVED, "description",
                    "The description can be at most 1000 characters long.");
        }
        return value == null || value.isEmpty() ? null : value;
    }

    /** A project always has an owner (decided 2026-11-12; DA-03 owner part rejected; FR-06). */
    static void requireOwner(String value) {
        if (value == null) {
            throw ApiException.validation(NOT_SAVED, "owner_member_id", "An owner is required.");
        }
    }

    /** When both dates are given, the start date is not after the due date (decided 2026-11-12, FR-06, BR-21). */
    static void requireDateOrder(LocalDate start, LocalDate due) {
        if (start != null && due != null && start.isAfter(due)) {
            throw ApiException.validation(NOT_SAVED, "due_date", "The due date cannot be before the start date.");
        }
    }

    private List<ProjectView> views(List<Project> list) {
        Map<UUID, String> workspaceNames = members.workspaceNames(
                list.stream().map(Project::getWorkspaceId).collect(Collectors.toSet()));
        Map<UUID, String> ownerNames = members.namesOf(list.stream().map(Project::getOwnerMemberId)
                .flatMap(id -> id == null ? Stream.empty() : Stream.of(id)).collect(Collectors.toSet()));
        return list.stream().map(p -> new ProjectView(p.getId(), p.getWorkspaceId(),
                        workspaceNames.get(p.getWorkspaceId()), p.getName(), p.getDescription(), p.getOwnerMemberId(),
                        p.getOwnerMemberId() == null ? null : ownerNames.get(p.getOwnerMemberId()),
                        p.getStartDate(), p.getDueDate(), p.isArchived()))
                .toList();
    }

    record ProjectPage(List<ProjectView> items, int page, int size, long total) {
    }
}
