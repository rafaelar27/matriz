package com.matriz.tasks;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.AccessPolicy;
import com.matriz.accesscontrol.Actor;
import com.matriz.projects.ProjectDirectory;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;
import com.matriz.workspaces.MemberDirectory;
import com.matriz.workspaces.MemberDirectory.WorkspaceMember;

/**
 * Task assignees (CMP-05; FR-22, US-29, BR-22; decided 2026-11-12). Access to the project and the task's project
 * match are checked by {@link TaskService}; permissions by {@link AccessPolicy}. Eligibility is never taken from the
 * client: every id is checked against the active members of the task's project.
 */
@Service
class AssignmentService {

    static final int MAX_PER_REQUEST = 20;
    private static final String FIELD = "member_ids";
    private static final String NOT_ASSIGNED = "The members could not be assigned.";

    private final TaskService tasks;
    private final TaskAssigneeRepository assignees;
    private final ProjectDirectory projects;
    private final MemberDirectory members;
    private final AccessPolicy policy;

    AssignmentService(TaskService tasks, TaskAssigneeRepository assignees, ProjectDirectory projects,
                      MemberDirectory members, AccessPolicy policy) {
        this.tasks = tasks;
        this.assignees = assignees;
        this.projects = projects;
        this.members = members;
        this.policy = policy;
    }

    /** listEligibleAssignees: active members of the task's project, sorted by name (FR-07, BR-22). */
    @Transactional(readOnly = true)
    List<WorkspaceMember> eligible(UUID callerAccountId, UUID projectId, UUID taskId) {
        tasks.accessToProject(callerAccountId, projectId, TaskService.TASK_NOT_FOUND);
        tasks.load(projectId, taskId);
        return eligibleMembers(projectId);
    }

    /** listTaskAssignees: every member of the workspace may read them (FR-05). */
    @Transactional(readOnly = true)
    List<AssigneeView> list(UUID callerAccountId, UUID projectId, UUID taskId) {
        tasks.accessToProject(callerAccountId, projectId, TaskService.TASK_NOT_FOUND);
        tasks.load(projectId, taskId);
        return views(taskId);
    }

    /** assignTask: all ids are checked before anything is saved, so a refused request changes nothing. */
    @Transactional
    List<AssigneeView> assign(UUID callerAccountId, UUID projectId, UUID taskId, List<String> memberIds) {
        Actor caller = tasks.accessToProject(callerAccountId, projectId, TaskService.TASK_NOT_FOUND);
        tasks.load(projectId, taskId);
        policy.checkAssign(caller);

        if (memberIds == null || memberIds.isEmpty() || memberIds.size() > MAX_PER_REQUEST) {
            throw ApiException.validation(NOT_ASSIGNED, FIELD, "Send 1 to 20 member ids.");
        }
        Map<UUID, WorkspaceMember> eligible = eligibleMembers(projectId).stream()
                .collect(Collectors.toMap(WorkspaceMember::memberId, m -> m));
        List<UUID> ids = memberIds.stream().map(value -> Ids.parse(value).filter(eligible::containsKey)
                        .orElseThrow(() -> ApiException.validation(NOT_ASSIGNED, FIELD,
                                "Only active members of this project can be assigned.")))
                .toList();
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (!seen.add(id)) {
                throw ApiException.conflict(eligible.get(id).name() + " appears more than once in the request.", FIELD);
            }
        }
        Set<UUID> current = assignees.findAllByTaskId(taskId).stream().map(TaskAssignee::getMemberId)
                .collect(Collectors.toSet());
        for (UUID id : ids) {
            if (current.contains(id)) {
                throw ApiException.conflict(eligible.get(id).name() + " is already assigned to this task.", FIELD);
            }
        }
        try {
            ids.forEach(id -> assignees.insert(taskId, id));
            assignees.flush();
        } catch (DataIntegrityViolationException e) {
            // Another request assigned the same member in the meantime: the primary key refused it (BR-22).
            throw ApiException.conflict("A member is already assigned to this task.", FIELD);
        }
        return views(taskId);
    }

    /** unassignTask: removes one assignment; the others and the owner stay. */
    @Transactional
    void unassign(UUID callerAccountId, UUID projectId, UUID taskId, UUID memberId) {
        Actor caller = tasks.accessToProject(callerAccountId, projectId, TaskService.TASK_NOT_FOUND);
        tasks.load(projectId, taskId);
        policy.checkAssign(caller);
        if (assignees.remove(taskId, memberId) == 0) {
            throw ApiException.notFound("This member is not assigned to the task.");
        }
    }

    private List<WorkspaceMember> eligibleMembers(UUID projectId) {
        UUID workspaceId = projects.workspaceOf(projectId).orElseThrow();
        Set<UUID> projectMembers = projects.memberIdsOf(projectId);
        return members.activeMembersOf(workspaceId).stream()
                .filter(m -> projectMembers.contains(m.memberId()))
                .toList();
    }

    private List<AssigneeView> views(UUID taskId) {
        List<TaskAssignee> rows = assignees.findAllByTaskId(taskId);
        Map<UUID, String> names = members.namesOf(rows.stream().map(TaskAssignee::getMemberId).toList());
        return rows.stream()
                .map(a -> new AssigneeView(a.getMemberId(), names.getOrDefault(a.getMemberId(), ""), a.getAssignedAt()))
                .sorted(Comparator.comparing(AssigneeView::name).thenComparing(a -> a.memberId().toString()))
                .toList();
    }

    /** An assignee as returned by the API (openapi.yaml: Assignee): no email or other account data. */
    record AssigneeView(UUID memberId, String name, Instant assignedAt) {
    }
}
