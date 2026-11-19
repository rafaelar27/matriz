package com.matriz.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Permission rules added by INC-17 to INC-19 as pure logic (US-03, US-04, US-26; FR-03, FR-04, FR-05, FR-23, FR-24;
 * decisions of 2026-11-19 Q1, Q4, QE, QG). No Spring context, no database. Expected: only an active Workspace
 * Administrator manages a workspace (invite, rename, change roles, remove members, delete); Project Managers, Team
 * Members and Viewers get 403. A task whose creator was removed can be deleted by a Workspace Administrator or a
 * Project Manager only. A denial is a 403 with code {@code forbidden}.
 */
class WorkspaceManagementPolicyTest {

    private static final UUID ME = UUID.fromString("a0000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("a0000000-0000-0000-0000-0000000000b2");

    private final AccessPolicy policy = new AccessPolicy();

    private static void assertDenied(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.error().code()).isEqualTo("forbidden");
        });
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Q1, Q4, FR-03, FR-04, FR-23, FR-24 · manage workspace: Workspace Administrator allowed; Project Manager, Team Member and Viewer denied")
    void manageWorkspaceTable(Role role) {
        Runnable call = () -> policy.checkManageWorkspace(new Actor(ME, role, true));
        if (role == Role.WORKSPACE_ADMINISTRATOR) {
            assertThatCode(call::run).doesNotThrowAnyException();
        } else {
            assertDenied(call);
        }
    }

    @Test
    @DisplayName("Q4, AC-03.4 · exactly one role may manage a workspace")
    void onlyOneRoleManages() {
        long allowed = Arrays.stream(Role.values()).filter(r -> {
            try {
                policy.checkManageWorkspace(new Actor(ME, r, true));
                return true;
            } catch (ApiException e) {
                return false;
            }
        }).count();
        assertThat(allowed).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("SP-04, Q1 · a deactivated member never passes workspace access, whatever the role")
    void deactivatedMemberIsRefusedBeforeManaging(Role role) {
        assertThatThrownBy(() -> policy.requireWorkspaceAccess(Optional.of(new Actor(ME, role, false)), "n/a"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("QE, US-26, BR-12 · delete a task whose creator was removed (no creator): Workspace Administrator and Project Manager allowed; Team Member and Viewer denied")
    void deleteTaskWithoutCreator(Role role) {
        Runnable call = () -> policy.checkDeleteTask(new Actor(ME, role, true), null);
        if (role == Role.WORKSPACE_ADMINISTRATOR || role == Role.PROJECT_MANAGER) {
            assertThatCode(call::run).doesNotThrowAnyException();
        } else {
            assertDenied(call);
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("US-26, BR-12, AC-26.2 · delete a task that has a creator: still only that creator, and never a Viewer (QE does not widen it)")
    void deleteTaskWithCreatorStaysCreatorOnly(Role role) {
        Runnable byOther = () -> policy.checkDeleteTask(new Actor(ME, role, true), OTHER);
        assertDenied(byOther);
        Runnable byCreator = () -> policy.checkDeleteTask(new Actor(ME, role, true), ME);
        if (role == Role.VIEWER) {
            assertDenied(byCreator);
        } else {
            assertThatCode(byCreator::run).doesNotThrowAnyException();
        }
    }
}
