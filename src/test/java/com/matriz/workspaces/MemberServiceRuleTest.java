package com.matriz.workspaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.matriz.accesscontrol.Role;

/**
 * The "last Workspace Administrator" rule as pure logic (INC-18; US-04, US-05; FR-04, SP-07, BR-04, EC-03; decision
 * QG; AC-INC18.3). No Spring context, no database. Expected: a role change or removal is refused (409) exactly when
 * no active Workspace Administrator other than the target would remain; an administrator may demote or remove
 * themselves when another one remains.
 */
class MemberServiceRuleTest {

    private static Member member(Role role) {
        Member m = new Member(null, UUID.randomUUID(), role);
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        return m;
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PROJECT_MANAGER", "TEAM_MEMBER", "VIEWER"})
    @DisplayName("AC-INC18.3, EC-03, SP-07, BR-04 · the only administrator cannot take another role")
    void onlyAdministratorCannotBeDemoted(Role newRole) {
        Member only = member(Role.WORKSPACE_ADMINISTRATOR);
        assertThat(MemberService.leavesNoAdministrator(List.of(only), only, newRole)).isTrue();
    }

    @Test
    @DisplayName("AC-INC18.3, EC-03, SP-07, BR-04 · the only administrator cannot be removed (no new role)")
    void onlyAdministratorCannotBeRemoved() {
        Member only = member(Role.WORKSPACE_ADMINISTRATOR);
        assertThat(MemberService.leavesNoAdministrator(List.of(only), only, null)).isTrue();
    }

    @Test
    @DisplayName("AC-INC18.3 · giving the only administrator the Workspace Administrator role again leaves an administrator")
    void keepingTheAdministratorRoleIsAllowed() {
        Member only = member(Role.WORKSPACE_ADMINISTRATOR);
        assertThat(MemberService.leavesNoAdministrator(List.of(only), only, Role.WORKSPACE_ADMINISTRATOR)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PROJECT_MANAGER", "TEAM_MEMBER", "VIEWER"})
    @DisplayName("AC-INC18.3, QG · with a second administrator, either may be demoted, also the one demoting themselves")
    void anAdministratorCanBeDemotedWhenAnotherRemains(Role newRole) {
        Member first = member(Role.WORKSPACE_ADMINISTRATOR);
        Member second = member(Role.WORKSPACE_ADMINISTRATOR);
        List<Member> admins = List.of(first, second);
        assertThat(MemberService.leavesNoAdministrator(admins, first, newRole)).isFalse();
        assertThat(MemberService.leavesNoAdministrator(admins, second, newRole)).isFalse();
    }

    @Test
    @DisplayName("AC-INC18.3, QG · with a second administrator, either may be removed, also the one removing themselves")
    void anAdministratorCanBeRemovedWhenAnotherRemains() {
        Member first = member(Role.WORKSPACE_ADMINISTRATOR);
        Member second = member(Role.WORKSPACE_ADMINISTRATOR);
        List<Member> admins = List.of(first, second);
        assertThat(MemberService.leavesNoAdministrator(admins, first, null)).isFalse();
        assertThat(MemberService.leavesNoAdministrator(admins, second, null)).isFalse();
    }

    @Test
    @DisplayName("AC-INC18.3 · changing or removing a member who is not an administrator never leaves the workspace without one")
    void nonAdministratorTargetIsNeverTheLastAdministrator() {
        Member admin = member(Role.WORKSPACE_ADMINISTRATOR);
        Member viewer = member(Role.VIEWER);
        assertThat(MemberService.leavesNoAdministrator(List.of(admin), viewer, Role.TEAM_MEMBER)).isFalse();
        assertThat(MemberService.leavesNoAdministrator(List.of(admin), viewer, Role.WORKSPACE_ADMINISTRATOR)).isFalse();
        assertThat(MemberService.leavesNoAdministrator(List.of(admin), viewer, null)).isFalse();
    }
}
