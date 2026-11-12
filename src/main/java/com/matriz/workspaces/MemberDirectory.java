package com.matriz.workspaces;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.accesscontrol.Actor;
import com.matriz.accounts.AccountDirectory;
import com.matriz.accounts.MembershipProvider;

/**
 * Public interface of CMP-02 for other modules: memberships, roles, member names and workspace names. Read from the
 * database on every call, so role changes and deactivation apply to the next request (SP-03, SP-04).
 */
@Service
public class MemberDirectory implements MembershipProvider {

    private final MemberRepository members;
    private final WorkspaceRepository workspaces;
    private final AccountDirectory accounts;

    MemberDirectory(MemberRepository members, WorkspaceRepository workspaces, AccountDirectory accounts) {
        this.members = members;
        this.workspaces = workspaces;
        this.accounts = accounts;
    }

    /** The account's membership in the workspace, as facts for Access Control. Empty if it is not a member. */
    @Transactional(readOnly = true)
    public Optional<Actor> actorIn(UUID accountId, UUID workspaceId) {
        return members.findMembership(accountId, workspaceId)
                .map(m -> new Actor(m.getId(), m.getRole(), m.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> activeMembershipsOf(UUID accountId) {
        return members.findAllByAccount(accountId).stream()
                .filter(Member::isActive)
                .map(m -> new Membership(m.getId(), m.getWorkspace().getId(), m.getWorkspace().getName(),
                        m.getRole().label()))
                .toList();
    }

    /** Whether the member exists, is active and belongs to the workspace (FR-06 project owner, NFR-01). */
    @Transactional(readOnly = true)
    public boolean isActiveMemberOf(UUID memberId, UUID workspaceId) {
        return members.findById(memberId)
                .filter(m -> m.isActive() && m.getWorkspace().getId().equals(workspaceId))
                .isPresent();
    }

    /** Names of the people behind these memberships, by member id (to show owners). */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> memberIds) {
        List<Member> found = members.findAllById(memberIds);
        Map<UUID, String> names = accounts.namesOf(found.stream().map(Member::getAccountId).toList());
        return found.stream().filter(m -> names.containsKey(m.getAccountId()))
                .collect(Collectors.toMap(Member::getId, m -> names.get(m.getAccountId())));
    }

    /** Workspace names by id. */
    @Transactional(readOnly = true)
    public Map<UUID, String> workspaceNames(Collection<UUID> workspaceIds) {
        return workspaces.findAllById(workspaceIds).stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));
    }

    /** Active members of a workspace with their names and roles, sorted by name. */
    @Transactional(readOnly = true)
    public List<WorkspaceMember> activeMembersOf(UUID workspaceId) {
        List<Member> active = members.findAllByWorkspace(workspaceId).stream().filter(Member::isActive).toList();
        Map<UUID, String> names = accounts.namesOf(active.stream().map(Member::getAccountId).toList());
        return active.stream()
                .map(m -> new WorkspaceMember(m.getId(), names.getOrDefault(m.getAccountId(), ""), m.getRole().label()))
                .sorted(Comparator.comparing(WorkspaceMember::name).thenComparing(w -> w.memberId().toString()))
                .toList();
    }

    /** A workspace member as offered for project ownership. */
    public record WorkspaceMember(UUID memberId, String name, String role) {
    }
}
