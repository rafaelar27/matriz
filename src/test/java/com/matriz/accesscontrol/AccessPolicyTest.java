package com.matriz.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Permission decision table as pure logic (test-plan matrix; INC-02 access rules; US-06, US-10, US-11, US-15, US-19,
 * US-25, US-26, US-29). No Spring context, no database. Expected values come from openapi.yaml 0.7.0-draft and the
 * decision log. Cells marked "prov." follow a provisional rule (PD-01, PD-03, PD-17, PD-20) as documented.
 * A denial is a 403 with code {@code forbidden}; message text is never asserted.
 */
class AccessPolicyTest {

    private static final UUID ME = UUID.fromString("a0000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("a0000000-0000-0000-0000-0000000000b2");
    private static final Role WA = Role.WORKSPACE_ADMINISTRATOR;
    private static final Role PM = Role.PROJECT_MANAGER;
    private static final Role TM = Role.TEAM_MEMBER;
    private static final Role V = Role.VIEWER;

    private final AccessPolicy policy = new AccessPolicy();

    private static Actor actor(Role role) {
        return new Actor(ME, role, true);
    }

    private static void assertAllowed(Runnable call) {
        assertThatCode(call::run).doesNotThrowAnyException();
    }

    private static void assertDenied(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.error().code()).isEqualTo("forbidden");
        });
    }

    private static void check(boolean allowed, Runnable call) {
        if (allowed) {
            assertAllowed(call);
        } else {
            assertDenied(call);
        }
    }

    private static Stream<Arguments> manageProject() {
        return Stream.of(Arguments.of(WA, false), Arguments.of(PM, true), Arguments.of(TM, false),
                Arguments.of(V, false));
    }

    @ParameterizedTest(name = "{0} manage project -> allowed={1}")
    @MethodSource("manageProject")
    @DisplayName("FR-06, FR-05 · manage project: only Project Manager (Workspace Administrator denied, prov. PD-03)")
    void manageProjectTable(Role role, boolean allowed) {
        check(allowed, () -> policy.checkManageProject(actor(role)));
    }

    private static Stream<Arguments> createProject() {
        return Stream.of(Arguments.of(WA, true), Arguments.of(PM, true), Arguments.of(TM, false),
                Arguments.of(V, false));
    }

    @ParameterizedTest(name = "{0} create project -> allowed={1}")
    @MethodSource("createProject")
    @DisplayName("FR-06, FR-05 · create project: Project Manager and Workspace Administrator allowed; Team Member and Viewer denied")
    void createProjectTable(Role role, boolean allowed) {
        check(allowed, () -> policy.checkCreateProject(actor(role)));
    }

    private static Stream<Arguments> createTask() {
        return Stream.of(
                Arguments.of(WA, true, false), Arguments.of(PM, true, true), Arguments.of(TM, true, true),
                Arguments.of(V, true, false),
                Arguments.of(WA, false, false), Arguments.of(PM, false, false), Arguments.of(TM, false, false),
                Arguments.of(V, false, false));
    }

    @ParameterizedTest(name = "{0} project member={1} create task -> allowed={2}")
    @MethodSource("createTask")
    @DisplayName("FR-09, BR-12, FR-05, AC-19.2, TC-07 · create task: PM/TM who are project members only (prov. PD-03, PD-20)")
    void createTaskTable(Role role, boolean projectMember, boolean allowed) {
        check(allowed, () -> policy.checkCreateTask(actor(role), projectMember));
    }

    private static Stream<Arguments> assignOwner() {
        return Stream.of(Arguments.of(PM, true, true), Arguments.of(PM, false, true), Arguments.of(TM, true, true),
                Arguments.of(TM, false, false));
    }

    @ParameterizedTest(name = "{0} owner is self={1} -> allowed={2}")
    @MethodSource("assignOwner")
    @DisplayName("FR-10 · owner at creation: PM self or other; TM only self (prov. PD-01)")
    void ownerAtCreationTable(Role role, boolean self, boolean allowed) {
        check(allowed, () -> policy.checkAssignOwner(actor(role), self ? ME : OTHER));
    }

    private static Stream<Arguments> changeStatus() {
        return Stream.of(
                Arguments.of(PM, true, true), Arguments.of(TM, true, true),
                Arguments.of(PM, false, false), Arguments.of(TM, false, false),
                Arguments.of(WA, true, false), Arguments.of(WA, false, false),
                Arguments.of(V, true, false), Arguments.of(V, false, false));
    }

    @ParameterizedTest(name = "{0} owner={1} change status -> allowed={2}")
    @MethodSource("changeStatus")
    @DisplayName("FR-12, FR-05, AC-11.3 · change status: owner PM/TM only (prov. PD-01, PD-03, PD-17)")
    void changeStatusTable(Role role, boolean owner, boolean allowed) {
        check(allowed, () -> policy.checkChangeStatus(actor(role), owner ? ME : OTHER));
    }

    private static Stream<Arguments> editContent() {
        return Stream.of(
                Arguments.of(PM, true, true), Arguments.of(PM, false, true),
                Arguments.of(TM, true, true), Arguments.of(TM, false, false),
                Arguments.of(WA, true, false), Arguments.of(WA, false, false),
                Arguments.of(V, true, false), Arguments.of(V, false, false));
    }

    @ParameterizedTest(name = "{0} owner={1} edit title/description/due date -> allowed={2}")
    @MethodSource("editContent")
    @DisplayName("FR-09, FR-05, AC-10.4 · edit content: PM any, TM only as owner (prov. PD-01, PD-03, PD-17)")
    void editContentTable(Role role, boolean owner, boolean allowed) {
        check(allowed, () -> policy.checkEditTask(actor(role), owner ? ME : OTHER, true, false, false));
    }

    private static Stream<Arguments> changeOwner() {
        return Stream.of(
                Arguments.of(PM, true, true), Arguments.of(PM, false, true),
                Arguments.of(TM, true, false), Arguments.of(TM, false, false),
                Arguments.of(WA, true, false), Arguments.of(V, true, false),
                Arguments.of(WA, false, false), Arguments.of(V, false, false));
    }

    @ParameterizedTest(name = "{0} owner={1} change owner -> allowed={2}")
    @MethodSource("changeOwner")
    @DisplayName("AC-10.4, FR-10 · change owner: PM only, TM denied even as owner (prov. PD-01, PD-03, PD-17)")
    void changeOwnerTable(Role role, boolean owner, boolean allowed) {
        check(allowed, () -> policy.checkEditTask(actor(role), owner ? ME : OTHER, false, true, false));
    }

    private static Stream<Arguments> statusOnlyEdit() {
        return Stream.of(
                Arguments.of(WA, true, false), Arguments.of(WA, false, false),
                Arguments.of(V, true, false), Arguments.of(V, false, false));
    }

    @ParameterizedTest(name = "{0} owner={1} status-only update -> allowed={2}")
    @MethodSource("statusOnlyEdit")
    @DisplayName("FR-12, FR-05, AC-11.3 · status-only update through the edit check: Viewer and Workspace Administrator refused (prov. PD-03, PD-17)")
    void statusOnlyEditTable(Role role, boolean owner, boolean allowed) {
        check(allowed, () -> policy.checkEditTask(actor(role), owner ? ME : OTHER, false, false, true));
    }

    private static Stream<Arguments> combinations() {
        return Stream.of(
                Arguments.of("PM non-owner content+status", PM, false, true, false, true, false),
                Arguments.of("TM owner content+owner", TM, true, true, true, false, false),
                Arguments.of("TM owner content+status", TM, true, true, false, true, true),
                Arguments.of("PM owner all three", PM, true, true, true, true, true));
    }

    @ParameterizedTest(name = "{0} -> allowed={6}")
    @MethodSource("combinations")
    @DisplayName("TU-07 · FR-09, FR-12 · update with several field groups is all or nothing (prov. PD-01)")
    void combinationsTable(String label, Role role, boolean owner, boolean content, boolean ownerField,
                           boolean status, boolean allowed) {
        check(allowed, () -> policy.checkEditTask(actor(role), owner ? ME : OTHER, content, ownerField, status));
    }

    private static Stream<Arguments> deleteTask() {
        return Stream.of(
                Arguments.of(PM, true, true), Arguments.of(TM, true, true),
                Arguments.of(PM, false, false), Arguments.of(TM, false, false),
                Arguments.of(WA, true, false), Arguments.of(WA, false, false),
                Arguments.of(V, true, false), Arguments.of(V, false, false));
    }

    @ParameterizedTest(name = "{0} creator={1} delete task -> allowed={2}")
    @MethodSource("deleteTask")
    @DisplayName("BR-12, AC-26.1, AC-26.2, test-plan TD-03 · delete task: only the creator, never Viewer (prov. PD-03)")
    void deleteTaskTable(Role role, boolean creator, boolean allowed) {
        check(allowed, () -> policy.checkDeleteTask(actor(role), creator ? ME : OTHER));
    }

    private static Stream<Arguments> assign() {
        return Stream.of(Arguments.of(PM, true), Arguments.of(TM, false), Arguments.of(V, false),
                Arguments.of(WA, false));
    }

    @ParameterizedTest(name = "{0} assign/unassign -> allowed={1}")
    @MethodSource("assign")
    @DisplayName("FR-22, AC-29.5 · assign and unassign: Project Manager only (prov. PD-01)")
    void assignTable(Role role, boolean allowed) {
        check(allowed, () -> policy.checkAssign(actor(role)));
    }

    private static Stream<Arguments> comment() {
        return Stream.of(
                Arguments.of(PM, true, true), Arguments.of(TM, true, true),
                Arguments.of(PM, false, false), Arguments.of(TM, false, false),
                Arguments.of(V, true, false), Arguments.of(V, false, false),
                Arguments.of(WA, true, false), Arguments.of(WA, false, false));
    }

    @ParameterizedTest(name = "{0} project member={1} comment -> allowed={2}")
    @MethodSource("comment")
    @DisplayName("FR-16, AC-15.1, AC-15.4, FR-05 · comment: PM/TM who are project members (prov. PD-03, PD-17, PD-20)")
    void commentTable(Role role, boolean projectMember, boolean allowed) {
        check(allowed, () -> policy.checkComment(actor(role), projectMember));
    }

    @ParameterizedTest(name = "{0} in workspace -> allowed")
    @MethodSource("allRoles")
    @DisplayName("FR-08, AC-19.1 · workspace access: an active member of any role is allowed")
    void activeMemberOfAnyRoleHasWorkspaceAccess(Role role) {
        Actor a = actor(role);
        assertThat(policy.requireWorkspaceAccess(Optional.of(a), "x")).isEqualTo(a);
    }

    private static Stream<Role> allRoles() {
        return Stream.of(Role.values());
    }

    @Test
    @DisplayName("NFR-01, SP-02, API-A3 · not a member of the workspace gives 404 not_found")
    void nonMemberGetsNotFound() {
        assertThatThrownBy(() -> policy.requireWorkspaceAccess(Optional.empty(), "x"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.error().code()).isEqualTo("not_found");
                });
    }

    @ParameterizedTest(name = "{0} deactivated -> refused")
    @MethodSource("allRoles")
    @DisplayName("SP-04 · workspace access: a deactivated member is refused (403 or 404; Q-03 open)")
    void deactivatedMemberIsRefused(Role role) {
        Consumer<ApiException> refused = e -> assertThat(e.status()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> policy.requireWorkspaceAccess(Optional.of(new Actor(ME, role, false)), "x"))
                .isInstanceOfSatisfying(ApiException.class, refused);
    }
}
