# Matriz — Test Plan: Tasks and Permissions

> **Status:** reviewed plan, written on 2026-11-17. Expected results come only from the documentation ([requirements.md](requirements.md), [user-stories.md](user-stories.md), [constraints.md](constraints.md), [database.md](database.md) and [openapi.yaml](../openapi.yaml)), never from the behavior of the implementation. No code was read to write this plan. Part of the plan was later automated and run on 2026-11-17 (see [Integration test results](#integration-test-results)). One end-to-end flow (create a task, AC-10.1) was automated and run on 2026-11-18 (see [End-to-end test results](#end-to-end-test-results)). Rules marked **prov.** are provisional until their pending decisions (PD-01, PD-03, PD-17, PD-20) are made, and the matching scenarios must change with them.

## Purpose and scope

**Purpose:** define verifiable scenarios for tasks and the permissions around them, so that tests can be written later from the documented requirements.

**In scope:** create, list, get, update (title, description, due date, owner, status), delete, validation (title, description, status, owner, due date, defaults, unknown fields), and access by users with and without permission, including direct API requests with changed identifiers. Requirements: FR-05, FR-09 to FR-13, FR-15, FR-18 (progress effects only), NFR-01, NFR-03, SP-01 to SP-04. Stories: US-10, US-11, US-14, US-19, US-25, US-26.

**Out of scope:** see [Scenarios intentionally excluded](#scenarios-intentionally-excluded).

## Test levels

| Level | Meaning |
|---|---|
| **Unit** | An isolated rule without database or authentication, for example title validation. Used only where the rule is isolated in code (Q-10, resolved 2026-11-17). Unit classes that exist (2026-11-17): `TaskTitleRuleTest`, `AccessPolicyTest`, `RoleTest`, `TaskStatusRuleTest`, `TaskDescriptionRuleTest`, `TaskEntityTest`, `ApiExceptionTest`, `ApiExceptionHandlerTest` and `IdsTest`; added 2026-11-18 (INC-10): `ProjectNameRuleTest`, `ProjectDescriptionRuleTest`, `ProjectDateOrderRuleTest`, `ProjectOwnerRuleTest`, `CommentBodyRuleTest`, `AssignmentRuleTest` and `TaskOwnerRuleTest` (see [Unit tests and coverage](#unit-tests-and-coverage-2026-11-17)). |
| **API** (Integration/API) | An HTTP request to the application with authentication and the database. Verifies status codes, bodies, persistence and permissions. |
| **E2E** (End-to-end) | Through the user interface. Used only where the acceptance criterion is about what the interface offers or says. One flow (TE-04) is automated with Playwright in `e2e/` (see [End-to-end test results](#end-to-end-test-results)); the rest (TE-01 to TE-03) are not. |

The lowest level that verifies the behavior reliably is chosen for each scenario. 4xx checks assert the HTTP status, `code` and `details[].field`, not message text (A7).

## Test data and users (fictitious)

Each scenario starts from this baseline. Unless a scenario says otherwise, requests carry a valid session and CSRF token.

| Item | Definition |
|---|---|
| W1 / W2 | Two workspaces: "Acme Test" and "Northwind Test" |
| Ana | Project Manager (PM) in W1, member of P1 and P2 |
| Paula | PM in W1, **not** a member of P1 |
| Ben | Team Member (TM) in W1, member of P1 |
| Tom | TM in W1, **not** a member of P1 |
| Vera | Viewer in W1, member of P1 |
| Carla | Workspace Administrator (WA) in W1 |
| Eli | Deactivated TM in W1, formerly a member of P1 |
| Dan | PM in W2 only, member of P3 |
| Projects | P1 and P2 in W1, P3 in W2 |
| T1 | In P1, created by Ana, owner Ben, `To do`, due 2026-11-06 |
| T2 | In P1, created by Ben, owner Ben, no due date |
| T3 | In P2, owner Ana |
| T4 | In P1, created by Ana, owner Vera |
| T5 | In P3, owner Dan |

Deactivating Eli and demoting a task creator need direct database setup, because no API exposes member management yet (Q-12).

## Scenario matrix

★ marks an essential scenario. "Unchanged" means the test re-reads the task and checks that nothing changed.

**Automation status (2026-11-17):** the matrix has 45 active scenarios (47 IDs minus the dropped TU-09 and TA-06). The scenarios checked in the Coverage increment are covered by the tests listed in [Coverage increment (2026-11-17)](#coverage-increment-2026-11-17), in `TaskIntegrationTest` (real HTTP, real sign-in), `TaskApiTest`, `TaskAccessTest` and `TaskTitleRuleTest` (TV-09, unit level). Scenarios not named in that list were not re-checked in the increment and rely on the existing test classes, whose coverage this plan does not state. TU-06 (invalid titles, description, owner on edit) and TA-05 (`PATCH` and `DELETE`, both directions) are covered by four tests added in the second coverage run (see [Coverage increment (2026-11-17)](#coverage-increment-2026-11-17)). Exceptions: TA-07 and TC-08 assert only "refused, no data" while Q-03 is open; TE-01 to TE-03 are browser-only and remain unautomated. One other end-to-end flow (create a task, AC-10.1) is automated with Playwright in `e2e/` (see [End-to-end test results](#end-to-end-test-results)); it is scenario TE-04, added on 2026-11-18 and not counted in the 45 above. Cases deliberately untested: Q-07 to Q-09 (see the open questions). No per-status count is given because the existing test classes were not mapped scenario by scenario.

| ID | Scenario | Preconditions | Input | Expected result | Level | Source |
|---|---|---|---|---|---|---|
| TC-01 | PM creates a task with every field | Ana signed in; Ben is a member of P1 | `POST /projects/P1/tasks` with title "Draft homepage text", description, owner Ben, due 2026-11-06 | 201. Body has project_id=P1, title, description, owner_member_id=Ben, owner_name, due_date, status `To do`, created_by=Ana. `Location` equals `/projects/P1/tasks/{task_id}` | API | AC-10.1; FR-09 to FR-12; BR-11; createTask |
| TC-02 ★ | A created task can be read back | TC-01 done; then Ben reads in a new request | `GET` the task; `GET` P1's task list; `GET /progress?project_id=P1` | 200 with the same fields as the 201 body. The task is in the list: `total` +1 and `status_counts` To do +1. Progress total +1, done unchanged. The `Location` link, followed with a GET in the same session, returns 200 with the same task_id | API | AC-10.1; FR-13; FR-18; NFR-03; A10 |
| TC-03 | Optional fields and default status | Ana | Only title and owner | 201; status `To do`; description and due_date null or absent | API | FR-12; BR-11; AC-10.1; A3 (DA-04, PD-04) |
| TC-04 | Explicit status at creation | Ana | status `In progress` | 201; status `In progress` | API | Decision log 2026-11-12; createTask |
| TC-05 | TM creates their own task | Ben | owner = Ben | 201; created_by=Ben; owner=Ben | API | FR-09; BR-12; createTask (prov. PD-01) |
| TC-06 | A Viewer who is a project member can own a task | Ana; Vera is a member of P1 | owner = Vera | 201; owner=Vera | API | FR-10; decision log 2026-10-29 |
| TC-07 | Creation refused by role or membership | Cases: (a) Vera; (b) Carla; (c) Ben with owner Ana; (d) Tom; (e) Paula | Otherwise valid body | 403 `forbidden`; no task created | API | (a) FR-05, AC-19.2; (b) PD-03 prov.; (c) PD-01 prov.; (d, e) PD-20 prov.; createTask |
| TC-08 | A deactivated member cannot create | Eli | Valid body | Refused, no task created. Status 403 vs 404 is open (Q-03) | API | SP-04; FR-03 |
| TV-01 ★ | Invalid title on create | Ana | title missing; `""`; `"   "`; `"ab"`; `"  ab  "`; 151 chars; `null` (A8) | 422 `validation_failed`, details field `title`. No task created (list `total` unchanged) | API | AC-10.2; AC-10.5; FR-09; BR-10 |
| TV-02 | Valid title boundaries | Ana | 3 chars; 150 chars; `"  abc  "`; 150 chars plus padding | 201 each. Title is returned trimmed (A4) | API | AC-10.5; FR-09 |
| TV-03 | Description limit | Ana | 2000 chars; 2001 chars | 201; 422 field `description` | API | FR-09; AC-25.2 |
| TV-04 | Invalid status | Ana | `"done"`, `"DONE"`, `"Doing"`, `""` | 422 field `status`; nothing created | API | FR-12; BR-11; AC-11.2; createTask |
| TV-05 | Invalid owner | Ana | owner missing; Tom (in W1, not in P1); Dan (W2); a nonexistent id | 422 field `owner_member_id`; nothing created | API | FR-10; BR-09; AC-10.3; NFR-01; createTask |
| TV-06 | Invalid due date | Ana | `"2026-02-30"`, `"31/12/2026"`, `"abc"` | 422 field `due_date` | API | FR-11; createTask |
| TV-07 | Unknown or identity fields | Ana | Extra field `created_by_member_id`; `priority`; `task_id` | 422; nothing created | API | openapi "Identity"; createTask; F17 out of scope; A5 |
| TV-08 | Malformed body | Ana | Invalid JSON; no body | 400 `malformed_request` | API | API-A2; A6 |
| TV-09 | Title rule as pure logic | None | Table: spaces-only, 2, 3, 150, 151 chars, padded | Accepts or rejects as in TV-01 and TV-02, with the trimmed value | Unit (only if the rule is isolated, Q-10) | FR-09; BR-10; AC-10.5 |
| TL-01 | Every active workspace member can list tasks | Vera, Carla, Tom, Ana in turn | `GET /projects/P1/tasks` | 200 with the same tasks for all four | API | FR-05; FR-08; FR-13; AC-19.1 |
| TL-02 | Order, fields, counts | P1 has: due 11-09 "bbb"; due 11-02 "aaa"; no due "Beta"; no due "alpha" | `GET` list | Order: 11-02, 11-09, then `alpha`, `Beta`. Each item has the Task fields. `status_counts` has the three statuses in order To do, In progress, Done | API | FR-13; decision log 2026-11-12; A9 |
| TL-03 | Filters combine | Mixed statuses and owners | status=`In progress`; owner=Ben; both | Only matching tasks; `total` is the filtered count; `status_counts` is unfiltered | API | FR-15; US-14; listTasks |
| TL-04 | Paging | 25 tasks | Default; `page=1&size=10` | Default size 20; correct slices; each task on exactly one page; total 25 | API | FR-13; listTasks |
| TL-05 | Invalid query parameters | Any member | status=`done`; size=0; size=101; page=-1; page=`x` | 422 | API | listTasks |
| TG-01 | Unknown task in an accessible project | Ana | `GET /projects/P1/tasks/{unknown id}` | 404 `not_found` | API | getTask; TaskNotFound |
| TG-02 | Task reached through the wrong project (same workspace) | Ana is a member of P1 and P2 | `GET /projects/P1/tasks/T3` | 404 | API | getTask |
| TU-01 | Allowed editors change title, description, due date | (a) Paula on T1; (b) Ben (owner) on T2 | `PATCH` `{title, description, due_date}` | 200 with the new values; owner, status, created_by unchanged | API | US-25; updateTask (prov. PD-01) |
| TU-02 | PM changes the owner | Ana; T1 owner Ben | `{owner_member_id: Ana}` | 200; Ana is the only owner; Ben is no longer owner | API | AC-10.4; FR-10 |
| TU-03 | Edit refused | (a) Ben edits T4; (b) Ben (owner of T1) sets owner; (c) Vera (owner of T4) edits; (d) Carla edits | Valid values | 403; unchanged | API | (a, b) updateTask, PD-01 prov.; (c) FR-05, AC-19.2, PD-17 prov.; (d) PD-03 prov. |
| TU-04 | Owner changes status | Ben, owner of T1 (To do) | `In progress`, then `Done` | 200 each. Reads reflect it; `status_counts` updates; progress done goes 0 to 1 on `Done` | API | AC-11.1; AC-11.2; AC-17.2; FR-12 |
| TU-05 | Status change refused | (a) Ana (PM, not owner) on T1; (b) Vera (owner) on T4; (c) Carla on T1 | `{status: Done}` | 403; status unchanged | API | (a) updateTask, PD-01 prov.; (b) AC-11.3, PD-17 prov.; (c) PD-03 prov. |
| TU-06 | Invalid values on edit | Ana | title `""`, spaces, `"ab"`, 151 chars, `null`; description 2001; status `"done"`; owner `null`; owner Tom; due `"abc"`; field `priority` | 422; task unchanged | API | AC-25.1; AC-25.2; AC-10.3; TaskValidationFailed |
| TU-07 | Permissions are checked before values, all or nothing | (a) Ana on T1; (b) Ben (owner) on T1 | (a) `{title: "New title", status: "Done"}`; (b) `{owner_member_id: Ana, title: ""}` | 403 in both cases (not 422). Title also unchanged | API | updateTask |
| TU-08 | Clear optional fields | Ana; T1 has a description and due date | `{description: null, due_date: null}` | 200; both null | API | updateTask; EC-06 |
| TD-01 | Creator deletes their own task | Ben created T2 | `DELETE /projects/P1/tasks/T2` | 204. Then `GET` gives 404 and the task is absent from the list. List `total`, `status_counts` and progress total each drop by 1 | API | AC-26.1; AC-17.3; FR-09; BR-12 |
| TD-02 | Deletion removes dependants | A task with at least 2 comments and 1 assignee (seeded) | Delete as creator | 204; no comment or assignee rows remain. Notifications: see A11 | API (with DB check) | AC-26.3; DA-05; database.md TaskAssignee |
| TD-03 | Deletion refused | (a) Ana deletes T2; (b) Vera; (c) Carla; (d) creator whose role is now Viewer | `DELETE` | 403; task still exists | API | AC-26.2; BR-12; deleteTask |
| TD-04 | Unknown or already deleted | Ben | `DELETE T2` twice | 204, then 404 | API | deleteTask; TaskNotFound |
| TA-01 | No session | None | List, create, get, patch, delete without a cookie | 401 `not_authenticated`; no data | API | FR-01; SP-01 |
| TA-02 | Missing CSRF header | Ana signed in | POST, PATCH, DELETE without `X-XSRF-TOKEN` | 403 `csrf_invalid` (different code from `forbidden`); nothing changed | API | openapi CsrfHeader |
| TA-03 ★ | Outsider cannot read | Dan (W2 only). Reverse: Ana on P3 | `GET /projects/P1/tasks`; `GET …/P1/tasks/T1`. Reverse: `GET /projects/P3/tasks` | 404 `not_found`, same status and code as for a random id; no task data | API | NFR-01; SP-02; BR-18; API-A3 (confirmed 2026-11-17) |
| TA-04 ★ | Outsider cannot modify | Dan | `POST /projects/P1/tasks`; `PATCH …/T1` `{title, status}`; `DELETE …/T1` | 404; T1 unchanged; no task created | API | NFR-01; SP-02; API-A3 (confirmed 2026-11-17) |
| TA-05 ★ | ID tampering: own project in the path, foreign task id | Dan (member of P3) | `GET`, `PATCH`, `DELETE /projects/P3/tasks/T1` | 404; T1 unchanged | API | NFR-01; getTask; updateTask; deleteTask ("must belong to project_id") |
| TA-07 | Deactivated member reads | Eli | `GET` P1 tasks | Refused, no data. Status 403 vs 404 is open (Q-03) | API | SP-04; FR-03 |
| TE-01 | No controls for denied roles | Vera on a task page; Ben on T1 (created by Ana) | Open the project and task | Vera sees no create, edit, delete or status control. Ben sees no Delete on T1 | E2E | AC-19.2; AC-11.3; AC-26.2 |
| TE-02 | Delete confirmation | Ben, T2 with 2 comments | Delete, cancel; delete, confirm | A prompt says it cannot be undone. Cancel keeps the task; confirm removes it | E2E | AC-26.3 |
| TE-03 | Title error in the form | Ana | Save with an empty title; with spaces only | No task created; a message states the rule (wording open, A7) | E2E | AC-10.2; AC-10.5 |
| TE-04 | Create a task through the interface | Ana signed in; demo data loaded | Sign in, open Website redesign, create a task with unique title, owner Ben Demo, due date, default status; reload | The task appears in the project's task list with that title, owner, due date and status shown as 'Por fazer' (To do); success message, no error; still listed after reload | E2E | AC-10.1; FR-01; FR-08; FR-09 to FR-13; NFR-03 |

## Essential scenarios

**1. Invalid title (TV-01, plus the title cases of TU-06 and TV-09)**
- **Risk and importance:** the title is the only mandatory field of a task. The rule (trimmed, 3 to 150 characters, spaces-only refused) is decided in FR-09, BR-10, AC-10.2 and AC-10.5. The risks are bad data in every list, and a client-only check that the API does not enforce.
- **Level:** API. Only a real request shows the 422, the field in `details`, and that nothing was saved. If the rule is an isolated unit, TV-09 holds the full boundary table cheaply and the API test keeps a few representative cases. A Unit test alone cannot prove that the endpoint refuses and saves nothing.

**2. Create then retrieve (TC-02, built on TC-01)**
- **Risk and importance:** it proves the core loop of FR-09 to FR-13: fields, the `To do` default, the server-set creator, visibility in the list and counts, and persistence (NFR-03).
- **Level:** API with the database, and the read must be a separate request. Unit tests cannot show persistence. E2E would be slower and less precise on fields and defaults.

**3. Unauthorized user (TA-03, TA-04, TA-05)**
- **Risk and importance:** NFR-01 and SP-02 are pass/fail requirements, and a leak across workspaces is the highest-severity defect. The scenarios cover read, modify, and a foreign task id under the user's own project. Each also checks that the target is unchanged.
- **Level:** API, because the attacker edits ids directly in requests, which an E2E interface cannot do. It needs authentication, two workspaces and the database. Unit tests cannot see the cross-record joins.

## Assumptions

Decided on 2026-11-17 by the programmer, who approved the proposed assumptions and recommendations as a whole ("go ahead with your recommendations"). The assumptions below are unconfirmed documentation-wise (AGENTS S4) and are recorded as **accepted for this plan**, not as project decisions. A1 was confirmed on 2026-11-17 (decision log).

| # | Assumption | Basis | Used in | Decision |
|---|---|---|---|---|
| A1 | A project or task in another workspace returns 404, identical to "not found". | API-A3, confirmed 2026-11-17; NFR-01 alone does not fix the code | TA-03 to TA-05 | Accepted, then **confirmed 2026-11-17** (decision log) |
| A2 | The provisional permission rules are asserted as written. | Decision log 2026-11-12 (PD-01, PD-03, PD-17, PD-20) | TC-07, TU-03, TU-05, TD-03 | Accepted (Q-06 resolved: test now, marked prov.) |
| A3 | Description and due date are optional; omitted means null or absent. | DA-04, API-A5; PD-04 pending | TC-03 | Accepted |
| A4 | The stored and returned title is trimmed, not only validated. | FR-09 | TV-02 | Accepted |
| A5 | `priority` is an unknown field and gets 422. | additionalProperties false; F17 out of scope | TV-07, TU-06 | Accepted (Q-01 resolved: no positive priority scenarios) |
| A6 | Malformed JSON gets 400. | API-A2 | TV-08 | Accepted |
| A7 | Tests assert status, `code` and `details[].field`, never message text. | Wording not fixed (NFR-09, PD-13) | All 4xx, TE-03 | Accepted |
| A8 | `title: null` on create gets 422. | API-A2 by analogy | TV-01 | Accepted |
| A9 | The tie-break "title ignoring case, then id" and the unfiltered `status_counts` are expected as stated. | openapi `listTasks` only | TL-02, TL-03 | Accepted |
| A10 | Persistence is proven by reading in a separate request; the automated tests do not restart the application. A restart check was done manually on 2026-11-17 (see Coverage increment and INC-08 AC-INC08.14; headless-browser script on a scratch database, not automated). | NFR-03 gives no method | TC-02 | Accepted |
| A11 | Comments and assignees are seeded and cascade-checked. Notifications cannot be created through the API yet, so they are seeded directly or left out. | AC-26.3; createTask "Not yet implemented" | TD-02 | Accepted |

## Open questions and documentation gaps

These stay open until the project decision-maker answers them (KC-01). No scenario guesses an answer.

| ID | Question or gap | Related IDs | Effect on the plan |
|---|---|---|---|
| Q-03 | What does a deactivated member get, 403 or 404? `Forbidden` lists "deactivated"; `ProjectNotFound` implies "not in the workspace". | SP-04, FR-03 | TC-08 and TA-07 assert only "refused, no data" |
| Q-07 | Does a title of tabs, newlines or non-breaking spaces count as "only spaces"? How are characters counted for non-ASCII text? | FR-09, BR-10 | Untested |
| Q-08 | What happens with a wrong JSON type (for example `title: 123`) and with a due date in the past on creation? | API-A2, FR-11 | Untested |
| Q-09 | What does a malformed id in the path return, 404 or 422? | API-A4 | Untested |
| Q-10 | Is the title rule an isolated unit? **Resolved 2026-11-17:** yes, `TaskService.title`, automated as TV-09 in `TaskTitleRuleTest` (5 tests). | FR-09 | TV-09 exists at Unit level |
| Q-11 | What is the `Location` header value? The `openapi.yaml` example was corrected on 2026-11-17 to a project-scoped path, but the format is not stated as a rule. **Resolved 2026-11-18:** the createTask `Location` is the project-scoped path `/projects/{project_id}/tasks/{task_id}` (openapi.yaml createTask is the rule; controller and tests aligned after a Codex finding on PR #6). | createTask | TC-01 asserts the exact value; the integration test also follows the link with a GET (200) |
| Q-12 | Baseline fixtures: for the integration test class (`TaskIntegrationTest`) they come from the demo seeder, with cleanup by created task id (decided in the approved plan, 2026-11-17). Deactivating Eli and demoting a creator need direct setup. | — | Test-tooling choice for the programmer |
| Q-13 | Does an empty `PATCH {}` by a Viewer or Workspace Administrator return 403 or 200 with no change? `openapi.yaml` `updateTask` says both "a body with no fields changes nothing" and "Viewers and Workspace Administrators cannot change tasks"; it does not say which wins. `TaskApiTest.viewersAndWorkspaceAdministratorsReadButNeverChange` currently expects 403. | updateTask; FR-05; AC-19.2 | Expected result of that case is unconfirmed |
| Q-14 | Which `details[].field` is returned for an unknown field such as `priority` or `task_id`? `openapi.yaml` names `details[].field` only for `created_by_member_id`. | createTask; TV-07; A5 | TV-07 asserts 422 and nothing created, not the field name |
| Q-15 | `TaskAccessTest.viewerCannotCreateTasks` asserts message text, against A7 (tests assert status, `code` and `details[].field`, never message text). Keep, change or accept as an exception? | A7; NFR-09; PD-13 | Left as is; outside the Coverage increment |
| Q-16 | Existing tests without a scenario ID in their name stay unnamed. Should they be renamed or mapped to scenario IDs? | — | Coverage of those scenarios cannot be stated per scenario |
| Q-17 | `openapi.yaml` does not state whether a project mismatch (404, "task must belong to project_id") is checked before the permission check (403). `TaskApiTest.veraCannotChangeOrDeleteANorthwindTaskThroughHerOwnProject` asserts 404. This order is the tester's reading, not a documented rule. Confirm. | updateTask; deleteTask; NFR-01; API-A3 | Expected result of the TA-05 reverse case is an unconfirmed reading |
| Q-18 | US-01 (sign in, FR-01) and US-12 (see a project's tasks, FR-13) have no acceptance criteria. The sign-in and project-detail expectations of the E2E test rest on FR-01, FR-08, FR-13, UI-A1 and README.md, not on criteria. Writing criteria is a product decision for the project decision-maker (KC-01). | US-01; US-12; FR-01; FR-08; FR-13; UI-A1 | E2E steps 1 and 2 are traced to FR and UI-A1 only |

Resolved: Q-01 (no positive priority scenarios) and Q-06 (provisional rules are tested now and marked prov.), approved by the programmer on 2026-11-17. Q-02 (404 for outsiders), Q-04 (AC-10.3 reworded: the backend refuses a non-project owner) and Q-05 (`updateTaskStatus` removed from the contract; TU-09 and TA-06 dropped) were decided by the project decision-maker on 2026-11-17 and are in the [decision log](constraints.md#decision-log).

### Changes to other documents (applied 2026-11-17 on the programmer's request)

1. `docs/constraints.md`, decision log: added the decision that access to a project or task of another workspace returns 404 (API-A3 confirmed).
2. `docs/user-stories.md`, AC-10.3: reworded to say a request naming a non-project member as owner is refused; decision-log row added.
3. `openapi.yaml`, `createTask` 201 `Location` example: now `/projects/prj_website/tasks/tsk_001`.
4. `openapi.yaml`: `updateTaskStatus` (`PATCH /tasks/{task_id}`), its schema `TaskStatusUpdateRequest` and its references were removed; decision-log row added. `API-A3` is marked confirmed.

## Scenarios intentionally excluded

| Excluded | Reason |
|---|---|
| Task priority | F17 is outside the initial scope (decision log 2026-11-12). Only the negative check TV-07 remains |
| "My tasks" (FR-14) | No API operation in the contract |
| Overdue marking (FR-19) | Derived value, not in the Task API; time zone pending (PD-08) |
| Notifications (FR-17) | Side effect not implemented; belongs to another increment |
| Assignee and comment behavior (US-29, US-15) | Separate stories. Only their cascade on task deletion is tested (TD-02) |
| Concurrent edits (EC-07) | PD-11 and TD-07 pending |
| Moving a Done task to an earlier status | PD-06 pending |
| Tasks in archived projects | PD-05 pending |
| Accounts in several workspaces | PD-16 pending |
| Sign-in and session expiry | Covered by US-01, not by this plan |
| Empty-body `PATCH` and same-status `PATCH` | Contract-only behavior, low risk |
| Owner-picker UI | A UI detail; the backend refusal is covered by TV-05 |
| Wrong-type values, past due dates, malformed path ids, whitespace other than spaces | Documentation silent (Q-07 to Q-09) |

## Integration test results

Run on 2026-11-17 (UTC) for the class `src/test/java/com/matriz/TaskIntegrationTest.java`. Expected results come from `openapi.yaml` (`createTask`, `getTask`, `listTasks`), [user-stories.md](user-stories.md) and this plan. Assertions use status, `code` and `details[].field`, never message text (A7). The fictitious demo users of the seeder are used (Ana, Ben, Vera, Dan in the demo workspaces), not the fictitious users of the baseline table above.

### Environment setup

| Item | Value |
|---|---|
| Database server | Local PostgreSQL 16 cluster in the container |
| Role and database | Role `matriz`; database `matriz_test`, owned by that role. No development database exists in this environment |
| Schema | Created by Flyway migrations V1 to V4 at the first application-context start |
| Environment variables | `MATRIZ_DB_URL` and `MATRIZ_TEST_DB_URL`, both `jdbc:postgresql://localhost:5432/matriz_test`; `MATRIZ_DB_USER=matriz`; `MATRIZ_DB_PASSWORD` set in the shell environment only (not written anywhere). No `.env` file; the application was not running |
| HTTP client | Real HTTP on a random port with `java.net.http.HttpClient`, one cookie jar per user. Nothing mocked; no new dependency |
| Authentication | Real sign-in (`GET /auth/csrf`, then `POST /auth/sign-in` with `X-XSRF-TOKEN`) and the real CSRF header on every request that changes data |
| Test password | A fictitious test password, passed to the demo seeder; no password in the code |
| Isolation | Data is committed (no test-level rollback), in the isolated `matriz_test` database only |
| Baseline | `@BeforeEach` runs the demo seeder and asserts by JDBC the baseline: 5 tasks, 3 of them in "Website redesign" |
| Cleanup | Every created task id is recorded; `@AfterEach` deletes only those rows and asserts the baseline again |
| Persistence proof | A direct JDBC query on a fresh autocommit connection, plus a new session for another user |

### Results

| Test | Scenario | Expected result (status, fields, persistence) | Observed result | Status (Pass/Fail) | Source |
|---|---|---|---|---|---|
| `createTaskWithEveryFieldAndReadItBack` | TC-01, TC-02: Ana creates "Draft homepage text" in Website redesign with description, owner Ben, a future fictitious due date and no status; then reads it back | 201; `Location` equals `/projects/<project id of the request>/tasks/<task_id from the response body>`, and a GET of that link in the same session returns 200 with the same task_id; body has project_id, title, description, owner_member_id=Ben, owner_name "Ben Demo", due_date, status `To do`, created_by_member_id=Ana. Exactly one committed row with those values on a fresh connection. A new session for Vera (Viewer) reads the same fields. List `total` 3 to 4 and `To do` +1 | As expected | Pass | AC-10.1; FR-09 to FR-13; NFR-03; BR-11; createTask |
| `invalidTitleIsRefusedAndNothingIsCreated` | TV-01: titles `"   "`, 151 characters, `""` and missing | Each: 422 `validation_failed` with `details[].field` `title`. JDBC counts (5 total, 3 in Website redesign) and the `listTasks` total (3) unchanged | As expected | Pass | AC-10.2; AC-10.5; BR-10 |
| `unknownTaskInAccessibleProjectIsNotFound` | TG-01: well-formed, nonexistent task id in an accessible project | 404 `not_found` | As expected | Pass | getTask |
| `memberOfAnotherWorkspaceCannotReadOrCreate` | TA-03 and the create case of TA-04: Dan (Northwind Lab only) lists tasks, gets a task and creates a task on an Acme project | Each: 404 `not_found`, same status and code as TG-01, no task data in the body; JDBC count unchanged | As expected | Pass | NFR-01; SP-02; API-A3 (confirmed 2026-11-17) |
| `withoutSessionRequestsAreRefused` | TA-01 (subset): `createTask` and `getTask` without a session | 401 `not_authenticated`; JDBC count unchanged | As expected | Pass | FR-01; SP-01 |

### Runs

| Run | Command | Date (UTC) | Output summary | Database state after the run |
|---|---|---|---|---|
| 1 | `mvn verify` | 2026-11-17 12:17 | 145 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | Read-only `psql`: 5 tasks; 0 task ids not starting with `e0000000-` |
| 2 | `mvn verify` | 2026-11-17 12:19 | Same counts, BUILD SUCCESS | Same as run 1 |
| 3 | `mvn test -Dtest=TaskIntegrationTest` | 2026-11-17 12:19 | 5 tests, 0 failures, BUILD SUCCESS | Same as run 1 |

Tests per class in `mvn verify` (identical in runs 1 and 2): `ClientConfigControllerTest` 16, `AssignmentApiTest` 8, `TaskAccessTest` 21, `CsrfCookieTest` 1, `TaskIntegrationTest` 5, `TaskApiTest` 16, `VerificationApiTest` 12, `AuthenticationTest` 10, `DatabaseSetupTest` 3, `ClientConfigApiTest` 4, `ProjectApiTest` 42, `CommentApiTest` 7. Total 145. No repackage workaround was needed.

### Bugs found

None. Nothing was changed in production code, migrations or the contract.

### Not verified / not covered

- Persistence across an application restart (A10). Manual steps: start the application, sign in as Ana, create a task, stop and restart the application, sign in, `GET` the task. Expected: 200 with the same fields.
- TV-01 cases `"ab"`, `"  ab  "` and `null`, and the valid boundaries of 3 and 150 characters (TV-02): not in `TaskIntegrationTest`.
- The `/progress` check of TC-02.
- `PATCH` and `DELETE` scenarios.
- Sign-out and idle expiry.
- The reverse case of TA-03 (Ana on a Northwind project).
- TA-01 for list, `PATCH` and `DELETE`.
- The earlier total of 140 tests in README.md was not independently re-checked before this change; the observed total is now 145 (observed, not computed).

The results above are kept as first recorded on 2026-11-17. Where the list above says a case is not covered, the Coverage increment below supersedes it (the restart check was later done manually; see the Coverage increment).

### Coverage increment (2026-11-17)

Environment as in [Environment setup](#environment-setup): local PostgreSQL 16, database `matriz_test`, same environment-variable setup. Expected results come from this plan's matrix and `openapi.yaml`. Assertions use status, `code` and `details[].field`, never message text (A7). All results below were observed as expected.

**New or extended tests**

| Test | Scenario | Expected result | Observed | Pass/Fail | Source |
|---|---|---|---|---|---|
| `TaskIntegrationTest.createTaskWithEveryFieldAndReadItBack` (extended) | TC-02 | As in the matrix, plus `GET /progress`: total +1, done unchanged | As expected | Pass | AC-10.1; FR-13; FR-18; NFR-03; A10 |
| `TaskIntegrationTest.invalidTitleIsRefusedAndNothingIsCreated` (extended) | TV-01 | Titles `"ab"`, `"  ab  "` and `null`: 422, field `title`, nothing created | As expected | Pass | AC-10.2; AC-10.5; FR-09; BR-10 |
| `TaskIntegrationTest.withoutCsrfHeaderChangesAreRefused` (new) | TA-02 | `POST`, `PATCH` and `DELETE` without `X-XSRF-TOKEN` over the real cookie flow: 403 `csrf_invalid`; task unchanged | As expected | Pass | openapi CsrfHeader |
| `TaskApiTest.titleAndOwnerOnlyUseTheDefaults` | TC-03 | 201; status `To do`; description and due date null or absent | As expected | Pass | FR-12; BR-11; AC-10.1; A3 |
| `TaskApiTest.invalidDueDateOnCreateIsRefused` | TV-06 | 422, field `due_date` | As expected | Pass | FR-11; createTask |
| `TaskApiTest.unknownFieldsOnCreateAreRefused` | TV-07 | `priority` and `task_id`: 422; nothing created (field name not asserted, Q-14) | As expected | Pass | openapi "Identity"; createTask; F17 out of scope; A5 |
| `TaskApiTest.malformedBodyOnCreateIsABadRequest` | TV-08 | 400 `malformed_request` | As expected | Pass | API-A2; A6 |
| `TaskApiTest.everyActiveWorkspaceMemberListsTheSameTasks` | TL-01 | 200 with the same tasks for every active member | As expected | Pass | FR-05; FR-08; FR-13; AC-19.1 |
| `TaskApiTest.listOrderFieldsAndCounts` | TL-02 | Order, Task fields, `status_counts` in order | As expected | Pass | FR-13; decision log 2026-11-12; A9 |
| `TaskApiTest.pagingCoversEveryTaskOnce` (25 tasks) | TL-04 | Default size 20; each task on exactly one page; total 25 | As expected | Pass | FR-13; listTasks |
| `TaskApiTest.allowedEditorsChangeTitleDescriptionAndDueDate` | TU-01 | 200 with the new values; owner, status, created_by unchanged | As expected | Pass | US-25; updateTask (prov. PD-01) |
| `TaskApiTest.viewerWhoOwnsATaskCannotEditIt` | TU-03 (c) | 403; unchanged | As expected | Pass | FR-05; AC-19.2; PD-17 prov. |
| `TaskApiTest.workspaceAdministratorCannotEditATask` | TU-03 (d) | 403; unchanged | As expected | Pass | PD-03 prov. |
| `TaskApiTest.projectManagerWhoIsNotTheCreatorCannotDelete` | TD-03 (a) | 403; task still exists | As expected | Pass | AC-26.2; BR-12; deleteTask |
| `TaskApiTest.creatorWhoIsNowAViewerCannotDelete` | TD-03 (d) | 403; task still exists | As expected | Pass | AC-26.2; BR-12; deleteTask |
| `TaskApiTest.memberOfAcmeCannotReadNorthwindTasks` | TA-03 (reverse) | 404 `not_found`; no task data | As expected | Pass | NFR-01; SP-02; API-A3 |
| `TaskAccessTest.missingOrNonexistentOwnerIsRefused` | TV-05 | 422, field `owner_member_id`; nothing created | As expected | Pass | FR-10; BR-09; AC-10.3; NFR-01 |
| `TaskAccessTest.projectManagerOutsideTheProjectCannotCreateUntilPd20` | TC-07 (e) | 403 `forbidden`; nothing created | As expected | Pass | PD-20 prov.; createTask |
| `TaskAccessTest.permissionIsCheckedBeforeValuesForANonOwnerProjectManager` and `...ForATeamMemberSettingTheOwner` | TU-07 | 403, not 422; unchanged | As expected | Pass | updateTask |
| `TaskAccessTest.deactivatedMemberCannotReadTasks` | TA-07 | Status 403 or 404, no data (Q-03 open) | As expected | Pass | SP-04; FR-03 |
| `TaskTitleRuleTest` (new, 5 tests, package `com.matriz.tasks`, plain JUnit 5, no Spring and no database; calls the package-private static `TaskService.title`) | TV-09 | Accepts or rejects as in TV-01 and TV-02, with the trimmed value | As expected | Pass | FR-09; BR-10; AC-10.5 |
| `TaskApiTest.invalidTitlesOnEditAreRefusedAndTheRowIsUnchanged` (second run; new) | TU-06 (title) | Ana, `PATCH` on a Website redesign task with title `""`, `"   "`, `"ab"`, `"  ab  "` and 151 characters: 422 `validation_failed`, `details[].field` `title`; full row (title, description, due date, status, owner, creator) and task count unchanged | As expected | Pass | AC-25.1; AC-25.2; AC-10.5; BR-10; openapi TaskValidationFailed |
| `TaskApiTest.invalidDescriptionAndOwnerOnEditAreRefusedAndTheRowIsUnchanged` (second run; new) | TU-06 (description, owner) | Description of 2001 characters: 422, field `description`. `owner_member_id` null, owner Acme Carla (not a project member) and owner Northwind Carla (other workspace): 422, field `owner_member_id`. Row unchanged | As expected | Pass | TaskValidationFailed; AC-10.3 |
| `TaskApiTest.danCannotChangeOrDeleteAnAcmeTaskThroughHisOwnProject` (second run; new) | TA-05 | Dan (Project Manager of Northwind Lab only), own project Lab inventory in the path, Acme task `T_IMAGES`: `PATCH {"status":"Done"}`, `PATCH {"title":"Hijacked"}` and `DELETE` each give 404 `not_found`; no task data in the body; the Acme task and the task count unchanged | As expected | Pass | NFR-01; SP-02; API-A3 (confirmed 2026-11-17); updateTask; deleteTask ("must belong to project_id") |
| `TaskApiTest.veraCannotChangeOrDeleteANorthwindTaskThroughHerOwnProject` (second run; new) | TA-05 (reverse) | Same as the previous row for Vera (Acme only), Website redesign in the path, task `T_EQUIPMENT`: 404 `not_found` each; nothing changed. Asserts 404 for a project mismatch before any 403 permission check (see Q-17) | As expected | Pass | NFR-01; SP-02; API-A3; updateTask; deleteTask; Q-17 |

Display names of the four second-run tests start with the scenario ID. None of them changed production code.

**Existing tests extended** (error code and "nothing created" or "unchanged" added; all Pass): `TaskApiTest.statusMustBeOneOfTheThreeApiValues` (TV-04); `TaskApiTest.unknownFieldsAndInvalidValuesAreRefused` (TU-06, adds `due_date` `"abc"`); `TaskApiTest.deletingATaskRemovesItsCommentsAndNotificationsAndUpdatesTheCounts` (TD-01, progress total -1); `TaskApiTest.viewersAndWorkspaceAdministratorsReadButNeverChange` (TD-03 b and c; TU-03 d); `TaskApiTest.everyTaskOperationNeedsASession` (TA-01, code and unchanged); `TaskAccessTest` methods for TC-07 b, c and d (code and nothing created); `TaskAccessTest.teamMemberCreatesATaskTheyOwn` (TC-05, owner asserted); `TaskAccessTest.deactivatedMemberIsRefused` (TC-08, nothing created; exact 403 kept).

**Runs**

| Run | Command | Date (UTC) | Output summary | Database state after the run |
|---|---|---|---|---|
| 1 | `mvn verify` | 2026-11-17 | 170 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | 5 tasks; 0 task ids not like `e0000000-%`; title, status and owner of the 5 seeded tasks unchanged |
| 2 | `mvn verify` | 2026-11-17 | Same counts, BUILD SUCCESS | Same as run 1 |
| 3 | `mvn test -Dtest=TaskIntegrationTest` | 2026-11-17 | 6 tests, 0 failures, BUILD SUCCESS | Same as run 1 |
| 4 (independent, by the programmer) | `mvn verify` | 2026-11-17 | 170 tests, BUILD SUCCESS | Read-only `psql` afterwards: 5 tasks; 0 task ids not like `e0000000-%` |

A first attempt failed with "Connection refused" because the local PostgreSQL cluster was down. The cluster was restarted and that attempt is not counted.

Tests per class in `mvn verify` (identical in runs 1 and 2): `ClientConfigControllerTest` 16, `AssignmentApiTest` 8, `TaskAccessTest` 27, `CsrfCookieTest` 1, `TaskIntegrationTest` 6, `TaskTitleRuleTest` 5, `TaskApiTest` 29, `VerificationApiTest` 12, `AuthenticationTest` 10, `DatabaseSetupTest` 3, `ClientConfigApiTest` 4, `ProjectApiTest` 42, `CommentApiTest` 7. Total 170.

**Second coverage run (2026-11-17)**

The four tests added to `TaskApiTest` (rows marked "second run" above) use the same rollback-based style as the existing tests. Environment as before: local PostgreSQL 16, database `matriz_test`. The 170-test runs above are kept as history.

| Run | Command | Date (UTC) | Output summary | Database state after the run |
|---|---|---|---|---|
| 1 | `mvn verify` | 2026-11-17 | 174 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | 5 tasks; 0 task ids not like `e0000000-%`; title, status and owner of the 5 seeded tasks unchanged |
| 2 | `mvn verify` | 2026-11-17 | Identical counts, BUILD SUCCESS | Same as run 1 |
| 3 (independent, by the programmer) | `mvn verify` | 2026-11-17 | 174 tests, BUILD SUCCESS | Read-only `psql` afterwards: 5 tasks; 0 task ids not like `e0000000-%` |

Tests per class (identical in runs 1 and 2): `ClientConfigControllerTest` 16, `AssignmentApiTest` 8, `TaskAccessTest` 27, `CsrfCookieTest` 1, `TaskIntegrationTest` 6, `TaskTitleRuleTest` 5, `TaskApiTest` 33 (was 29), `VerificationApiTest` 12, `AuthenticationTest` 10, `DatabaseSetupTest` 3, `ClientConfigApiTest` 4, `ProjectApiTest` 42, `CommentApiTest` 7. Total 174.

Four of the TU-06 title values were already partly asserted by `VerificationApiTest.case04UpdateRefusesInvalidTitles`, which checks only the title column; plain `"ab"` on `PATCH` and the full-row check are new.

**Bugs found (Coverage increment and second coverage run):** None. Nothing was changed in production code, migrations or the contract in this increment, including the second coverage run.

**Not verified / not covered**

- Persistence across an application restart (A10) was checked manually on 2026-11-17, not automated, on a scratch database `matriz_verify` (not `matriz_test`). API level: Ben changed a task status through the project-scoped `PATCH` and a task was created; after an application restart the old session got 401 `session_expired`, and after signing in again the status and the created task were still there, the database read-back matched, and the removed `PATCH /tasks/{id}` returned 404. Interface level: a headless-browser script (Playwright, throwaway, not a committed test; a stand-in, not a person using a browser) changed a status to "Em curso"; it survived a page reload and an application restart, and the database read-back matched. There is still no automated restart test.
- Deliberately untested (Q-07 to Q-09): titles of tabs, newlines or non-breaking spaces; wrong JSON types; past due dates; malformed path ids.
- Sign-out and idle expiry for tasks.
- TE-01 to TE-03: browser-only; still not automated. A browser-test tool now exists for one other flow (create task, see [End-to-end test results](#end-to-end-test-results)), but TE-01 to TE-03 have no E2E test.
- TA-07 and TC-08 assert "refused, no data" only (Q-03).
- INC-08 steps are done: reviewer review, security audit, `docs/verification.md` Cases 05 and 08 re-verified, and the manual restart check; see INC-08 in [implementation-plan.md](implementation-plan.md) and [verification.md](verification.md). The restart check was a headless-browser script on a scratch database, not a human browser session and not an automated test. The INC-08 open follow-ups (Q-03, audit S-02 to S-04, INC-08 R-01 and R-03 optional minors) are not done and remain open. Q-11 and audit S-01 were resolved on 2026-11-18 (see [Location header fix (2026-11-18)](#location-header-fix-2026-11-18)).

## Unit tests and coverage (2026-11-17)

**Environment.** Database `matriz_test` on the local PostgreSQL 16 cluster, with the same environment-variable setup as in [Environment setup](#environment-setup); the variables are set in the shell only and nothing secret is written here. Coverage is measured with JaCoCo (`jacoco-maven-plugin` 0.8.14; goals `prepare-agent` and `report`, no `check` goal, no thresholds, no exclusions). The report is written to `target/site/jacoco` by `mvn verify`. A later Surefire `<argLine>` must contain `@{argLine}`, otherwise the JaCoCo agent is not attached and coverage silently reads 0%. (No `argLine` exists today, so coverage works.)

Expected results of the new unit tests come from `openapi.yaml` (0.7.0-draft), [requirements.md](requirements.md), [user-stories.md](user-stories.md), [database.md](database.md), the [decision log](constraints.md#decision-log) and this plan. Cells that depend on PD-01, PD-03, PD-17 or PD-20 are marked prov. The tests assert status and error code, never message text (A7). They use no Spring, no database and no mocking library, and no dependency or production code was added or changed for them (INC-09).

### Coverage before and after

Source: `jacoco.csv` (58 classes). "Before" is the 174-test baseline; "after" is the 276-test run (the 282-test runs after the review follow-up gave the same overall figures: 97.4% instructions, 88.0% branches, 98.3% lines; `AccessPolicy` 100/100/100; `accesscontrol` 99.0/96.4/100). Packages are under `com.matriz`. Instr = instructions, Br = branches, Line = lines; `n/a` = no branches.

| Package | Instr before | Br before | Line before | Instr after | Br after | Line after |
|---|---|---|---|---|---|---|
| Overall | 97.2% | 86.8% | 98.0% | 97.4% | 88.0% | 98.3% |
| `com.matriz` | 37.5% | n/a | 33.3% | 37.5% | n/a | 33.3% |
| `accesscontrol` | 97.8% | 92.9% | 98.3% | 99.0% | 96.4% | 100% |
| `accounts` | 98.6% | 70.0% | 98.5% | 98.6% | 70.0% | 98.5% |
| `comments` | 98.9% | 100% | 100% | 98.9% | 100% | 100% |
| `progress` | 92.5% | 75.0% | 95.8% | 92.5% | 75.0% | 95.8% |
| `projects` | 97.7% | 89.4% | 99.4% | 97.7% | 89.4% | 99.4% |
| `seed` | 99.3% | 50.0% | 98.9% | 99.3% | 50.0% | 98.9% |
| `shared` | 96.6% | 68.8% | 97.9% | 97.4% | 75.0% | 100% |
| `tasks` | 96.1% | 87.0% | 96.8% | 96.1% | 87.0% | 96.8% |
| `workspaces` | 97.2% | 100% | 100% | 97.2% | 100% | 100% |

Target classes (Instr / Br / Line):

| Class | Before | After |
|---|---|---|
| `AccessPolicy` | 96.7% / 95.7% / 96.4% | 100% / 100% / 100% |
| `Ids` | 81.8% / 50.0% / 80.0% | 100% / 100% / 100% |
| `ApiExceptionHandler` | 95.2% / 70.0% / 100% | 95.2% / 70.0% / 100% |
| `TaskService` | 100% / 93.8% / 100% | 100% / 93.8% / 100% |
| `Task`, `TaskStatus`, `TaskView`, `ApiException`, `Role` | 100% / n/a / 100% | 100% / n/a / 100% |

Reading of the figures: the integration tests already ran most of the code, so the overall figure barely moved. The new unit tests give a fast, exhaustive check of the permission table, where a failure points to one class. Coverage shows which code ran, not that its behavior is correct; it is not verification.

### New unit-test classes

| Class | Tests | Rules and IDs | Pass/Fail |
|---|---|---|---|
| `accesscontrol/AccessPolicyTest` | 77 (was 71) | Role-by-action decision table: manage project, create task as project member and as non-member, owner at creation, change status, edit content, change owner (now also refusals for a Viewer and a Workspace Administrator who is not the owner), status-only changes through `checkEditTask` (now also refusals for a Viewer and a Workspace Administrator, as owner and as non-owner; all 403 `forbidden`), all-or-nothing combinations, delete task, assign and unassign, comment, workspace access (non-member 404 `not_found`; deactivated member refused, status asserted only as 403 or 404, Q-03). Cells that depend on PD-01, PD-03, PD-17 and PD-20 are marked prov. The display name that cited "TD-03" now reads "test-plan TD-03", to avoid a clash with [constraints.md](constraints.md) TD-03. | Pass |
| `accesscontrol/RoleTest` | 1 | The four roles, labels in `openapi.yaml` order | Pass |
| `tasks/TaskStatusRuleTest` | 13 | Task status values and rule, including a `null` status (citation: openapi `TaskValidationFailed` (`null` added 2026-11-17), BR-11) | Pass |
| `tasks/TaskDescriptionRuleTest` | 3 | 2000 characters accepted; 2001 refused with 422 and field `description`; null clears | Pass |
| `tasks/TaskEntityTest` | 3 | Task entity | Pass |
| `shared/ApiExceptionTest` | 4 | 404, 403, 422 and 409 | Pass |
| `shared/ApiExceptionHandlerTest` | 3 | `ApiException` passed through; non-JSON body gives 400 `malformed_request`; unknown field gives 422 `validation_failed` (field name not asserted, Q-14) | Pass |
| `shared/IdsTest` | 4 | Identifier helper | Pass |

Total 108 new tests (77 + 1 + 13 + 3 + 3 + 4 + 3 + 4; was 102 before the review follow-up), plus the existing `TaskTitleRuleTest` (5, TV-09; see Q-10). The `AccessPolicy` code matched the decision table cell by cell.

### Runs

| Run | Command | Date (UTC) | Output summary | Total time | Database state after the run |
|---|---|---|---|---|---|
| Baseline | `mvn verify` | 2026-11-17 | 174 tests, 0 failures, BUILD SUCCESS | 1:04 min (includes the first JaCoCo download) | Not recorded for this run |
| 1 (tester) | `mvn verify` | 2026-11-17 | 276 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | 56.8 s | Not recorded for this run |
| 2 (tester) | `mvn verify` | 2026-11-17 | 276 tests, same counts, BUILD SUCCESS | 53.6 s | 5 tasks; 0 task ids not like `e0000000-%` |
| 3 (tester) | `mvn test -Dtest=<the 8 new classes>` | 2026-11-17 | 102 tests, 0 failures, BUILD SUCCESS | 3.8 s | Not recorded for this run |
| 4 (independent, by the main session) | `mvn verify` | 2026-11-17 | 276 tests, BUILD SUCCESS | 55.5 s | 5 tasks; 0 task ids not like `e0000000-%` |

| 5 (tester, after the review follow-up, run 1) | `mvn verify` | 2026-11-17 | 282 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | 55.2 s | 5 tasks; 0 task ids not like `e0000000-%` (checked once after runs 5 and 6) |
| 6 (tester, run 2) | `mvn verify` | 2026-11-17 | 282 tests, identical counts, BUILD SUCCESS | 55.0 s | As above |
| 7 (tester) | `mvn test -Dtest=AccessPolicyTest,TaskStatusRuleTest` | 2026-11-17 | 90 tests, 0 failures, BUILD SUCCESS | 6.3 s | Not recorded for this run |
| 8 (independent, by the main session) | `mvn verify` | 2026-11-17 | 282 tests, BUILD SUCCESS | 54.3 s | 5 tasks; 0 task ids not like `e0000000-%` |

Runs 1 to 4 are history for the 276-test state (174 + 102). Runs 5 to 8 are history for the 282-test state (174 + 108). Current state: 282 + 41 = 323 tests (see [INC-10 (2026-11-18)](#inc-10-2026-11-18)). `mvn test` writes `jacoco.exec` but no report; use `mvn test jacoco:report` for one.

### Review follow-up (2026-11-17)

The reviewer's findings on INC-09 (verdict approve with changes; 0 Blocker, 0 Major, 5 Minor) and what was done, all as recommended and approved by the programmer. These are INC-09 R-01 to R-05, not the INC-08 findings with the same numbers.

- INC-09 R-01: the JaCoCo `argLine` risk was undocumented. A comment was added to `pom.xml` and the sentence in the Environment note above; no `argLine` exists today.
- INC-09 R-02: the `AccessPolicyTest` display name cited "TD-03", which clashes with TD-03 in constraints.md. It now reads "test-plan TD-03".
- INC-09 R-03: the deactivated-member test accepts 403 or 404 on purpose (Q-03 open). No change; tighten it when Q-03 is decided.
- INC-09 R-04: the `null` status case in `TaskStatusRuleTest` was not stated in the contract. The rule is now stated in `openapi.yaml` (`TaskValidationFailed` and the `createTask` 422 triggers: "including `null`"; version not changed), and the test citation was changed to "openapi TaskValidationFailed (null added 2026-11-17), BR-11".
- INC-09 R-05: six refusal rows were added to `AccessPolicyTest` (Viewer and Workspace Administrator non-owner change-owner, 2; status-only through `checkEditTask` for Viewer and Workspace Administrator as owner and non-owner, 4), all 403 `forbidden`, marked prov. for PD-03 and PD-17. `AccessPolicyTest` is now 77 tests and the suite 282.

### INC-10 (2026-11-18)

Unit tests for the four refactoring candidates recorded in INC-09 (project, comment and assignment rules, and the task-owner membership/presence rule). Approved by the programmer (option A-narrow). Same environment as above: database `matriz_test` on local PostgreSQL 16, variables in the shell only. Unlike INC-09, production code was changed: commit `b92e516` refactored `src/main` without changing behaviour (same message literals, `ApiException` factories, fields and call order), moving the pure rules into package-private static methods `ProjectService.name`, `description`, `requireOwner`, `requireDateOrder`, `CommentService.body`, `AssignmentService.requireCount`, `requireNoRepeats` and `TaskService.requireOwner`. Checkpoint before any new test: `mvn verify` 282 tests, 0 failures, BUILD SUCCESS, 48.4 s. Commit `1d1b0ac` added the seven classes below (plain JUnit 5, AssertJ and JUnit params; no Spring, no database, no Mockito, no MockMvc); a follow-up commit applied the review fixes. The tests assert status, error code and `details[].field`, never message text (A7). Oracle: `openapi.yaml` 0.7.0-draft, FR-06, FR-10, FR-16, FR-22, BR-09, BR-13, BR-21, BR-22, BR-23, AC-15.2, EC-08 (comments) and the decision log; provisional rules are not involved.

| Class | Tests | Rules and IDs | Pass/Fail |
|---|---|---|---|
| `projects/ProjectNameRuleTest` | 10 | Name 3 to 100 characters after trimming: empty, spaces and `ab` refused 422 field `name`; `abc` and 100 characters accepted, also padded; 101 refused (FR-06, BR-21) | Pass |
| `projects/ProjectDescriptionRuleTest` | 3 | Description at most 1000: 1001 refused 422 field `description`; null returns null | Pass |
| `projects/ProjectDateOrderRuleTest` | 6 | Start equal to due accepted; start after due refused 422 field `due_date`; one date or none accepted (BR-23) | Pass |
| `projects/ProjectOwnerRuleTest` | 2 | Null owner refused 422 field `owner_member_id` | Pass |
| `comments/CommentBodyRuleTest` | 9 | Body null, empty or spaces refused 422 field `body`; `k` accepted; trimmed value returned; 2000 accepted, also padded; 2001 refused (FR-16, BR-13, AC-15.2, EC-08) | Pass |
| `tasks/AssignmentRuleTest` | 8 | `member_ids` count: null and empty refused 422 field `member_ids`; 1 and 20 accepted; 21 refused; repeats `[X,X]` and `[X,Y,X]` refused 409 `conflict` field `member_ids` (FR-22, BR-22, `assignTask` rule 1) | Pass |
| `tasks/TaskOwnerRuleTest` | 3 | Task owner null and empty refused 422 field `owner_member_id` (BR-09) | Pass |

Total 41 new tests (10 + 3 + 6 + 2 + 9 + 8 + 3).

Coverage (JaCoCo 0.8.14, `jacoco.csv`, 58 classes; Instr / Br / Line):

| Class | Before INC-10 (282 tests) | After INC-10 (323 tests) |
|---|---|---|
| Overall | 97.4% / 88.0% / 98.3% | 97.4% / 88.3% / 98.3% |
| `TaskService` | 100% / 93.8% / 100% | 100% / 95.8% / 100% (the empty-owner branch is now covered) |
| `AssignmentService` | 97.0% / 100% / 96.5% | 97.2% / 100% / 96.7% (the uncovered lines are still the concurrent-assignment race path) |
| `ProjectService` | 97.5% / 89.6% / 98.8% | 97.6% / 89.6% / 98.8% (the empty project description branch stays uncovered: undocumented) |
| `CommentService` | 98.2% / 100% / 100% | unchanged |

Reading of the figures: the integration tests already ran almost every branch; the gain is exact boundary tests whose failure points to one class. Coverage is not verification.

Runs (all on `matriz_test`):

| Run | Command | Date (UTC) | Output summary | Total time | Database state after the run |
|---|---|---|---|---|---|
| Checkpoint (after the refactor, before the new tests) | `mvn verify` | 2026-11-18 | 282 tests, 0 failures, BUILD SUCCESS | 48.4 s | Not recorded for this run |
| 9 (tester, before the split of bundled tests) | `mvn verify` | 2026-11-18 | 319 tests (37 in the 7 classes), BUILD SUCCESS | 39.3 s | Not recorded for this run |
| 10 (tester, before the split, run 2) | `mvn verify` | 2026-11-18 | 319 tests, same result | 39.2 s | Not recorded for this run |
| 11 (independent, by the main session, before the split) | `mvn verify` | 2026-11-18 | 319 tests, BUILD SUCCESS | 39.0 s | Not recorded for this run |
| 12 (tester, after the review fixes, run 1) | `mvn verify` | 2026-11-18 | 323 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | 40.8 s | 5 tasks; 0 task ids not like `e0000000-%` (checked once after runs 12 and 13) |
| 13 (tester, run 2) | `mvn verify` | 2026-11-18 | 323 tests, identical counts, BUILD SUCCESS | 40.8 s | As above |
| 14 (independent, by the main session) | `mvn verify` | 2026-11-18 | 323 tests, BUILD SUCCESS | 39.99 s | 5 tasks; 0 task ids not like `e0000000-%` (checked after the main session's own runs) |
| 15 (the 7 classes alone) | `mvn test` limited to the 7 classes | 2026-11-18 | 41 tests, 0 failures | 3.9 s | Not recorded for this run |

Current state: 282 + 41 = 323 tests, 0 failures, 0 errors, 0 skipped. Runs 9 to 11 are history for the 319-test state.

#### Review follow-up (INC-10, 2026-11-18)

The reviewer (read-only) on `b92e516` and `1d1b0ac`: verdict approve with changes, 0 Blocker, 0 Major, 7 Minor. A byte-for-byte comparison of every moved block and of the call order (access/permission before value checks; owner lookup skipped when the owner is null; assignment: count, eligibility, repeats, already assigned) found no behavioural change; `src/main` changed only in the four service files and there are no unused imports. These are INC-10 R-01 to R-07, not the INC-08 or INC-09 findings with the same numbers. No security-auditor run: no access code changed (decided PQ9).

- INC-10 R-01: four project tests cited "US-05" wrongly. Fixed: now US-06 and US-24.
- INC-10 R-02: AC-10.3 and EC-08 were cited for the project name. Fixed.
- INC-10 R-03: AC-29.3 was cited for the 1 to 20 count rule. Fixed: now FR-22, BR-22 and `assignTask` rule 1.
- INC-10 R-04: bundled inputs in `AssignmentRuleTest` and `ProjectDateOrderRuleTest`. Fixed by splitting into parameterised invocations (319 to 323 tests).
- INC-10 R-05: assert which repeated member is named in the 409. Not applied: undocumented.
- INC-10 R-06: empty project description untested. Not applied: `openapi.yaml` documents "null or an empty string clears it" only for the task description (`TaskUpdateRequest`); for the project it says only "null clears an optional field".
- INC-10 R-07: empty task owner id. Not applied: the `openapi.yaml` `Id` schema has `minLength` 1, so an empty id is invalid by the contract.

Not asserted on purpose (open or undocumented): `name: null`, whether the stored project name is trimmed, tabs or newlines as whitespace (Q-07), an empty-string project description stored as null, the membership and eligibility lookups, NFR-01, the check order, and which repeated member is named in the 409. No bug was found: all new tests pass against the refactored code.

### Location header fix (2026-11-18)

A Codex review of PR #6 found that the createTask `Location` header (`/tasks/{id}`, a path with no handler) disagreed with `openapi.yaml` (`/projects/{project_id}/tasks/{task_id}`). `TaskController.create` now returns the project-scoped path (commit e098c70). `TaskApiTest`, `TaskAccessTest` and `TaskIntegrationTest` assert the exact value, and `TaskIntegrationTest` follows the link with a GET (200, same task_id) (commit 7f76876). Q-11 is resolved. Two `mvn verify` runs by the tester and one independent run: 323 tests (unchanged count, assertions strengthened), 0 failures, 0 errors, 0 skipped, BUILD SUCCESS; `matriz_test` afterwards 5 tasks, 0 stray ids. The reviewer approved (0 Blocker, 0 Major, 2 Minor: this documentation update, and the readability of a test matcher, not applied).

### Still not covered, and why

- Rules that were tested only at API level and are now also unit-tested (INC-10, 2026-11-18), for the pure parts only: project name, description, date-order and owner rules (BR-21, BR-23, FR-06); the comment body rule (BR-13, AC-15.2); the assignment count and repeats rules (FR-22, BR-22); the task-owner presence rule (BR-09). The lookups (active-member, project-member, eligibility, already assigned), NFR-01 and the check order remain tested only through the API tests, by design. Not asserted because open or undocumented: `name: null`, stored-name trimming, tabs and newlines (Q-07), an empty-string project description, which repeated member the 409 names.
- History (INC-09 state): these were then listed as **refactoring candidates**, tested only at API level: project name, description, date-order and owner rules in `ProjectService.apply` (BR-21, BR-23, FR-06); the comment body rule in `CommentService.add` (BR-13, AC-15.2); the assignment rules in `AssignmentService` (1 to 20 ids, duplicates; FR-22, BR-22); the owner-membership check in `TaskService.owner` (BR-09).
- Packages with the lowest branch coverage after this run: `seed` 50.0%, `accounts` 70.0%, `progress` 75.0%, `shared` 75.0%. Not investigated further.
- Deliberately not tested, open questions: an empty `PATCH` by a Viewer or Workspace Administrator (Q-13); the exact status for a deactivated member (Q-03); an empty-string description; a creator who is no longer in the project; moving a task back from Done (PD-06).

### Bugs found

None found in INC-10. Production code WAS changed in INC-10 (a behaviour-preserving extraction in four service files in `src/main`, commit b92e516); nothing was changed in migrations or the contract.

### Not verified

- No person has reviewed the JaCoCo HTML report.
- The effect of the JaCoCo agent on build time was measured only as total time (the baseline includes a one-off download).
- The Q-03 and Q-13 cells (see above): their expected results are unconfirmed.

## End-to-end test results

Observed on 2026-11-18. One end-to-end flow, create a task through the user interface, is automated. TE-01 to TE-03 remain unautomated. No production code was changed.

### Tool and environment

| Item | Value |
|---|---|
| Tool | `@playwright/test` 1.63.0 (Node 22.23.5, npm 10.9.9) in the isolated folder `e2e/`, with `@types/node` 22.20.5 (typings only, a dev dependency) and `e2e/tsconfig.json`; `npx --no-install tsc --noEmit -p .` from `e2e/` type-checks with no errors (observed 2026-11-18, using a TypeScript 6.0.2 `tsc` already on the machine; `typescript` is not a dependency of `e2e/`). It is not part of `mvn verify` |
| Browser | Playwright's own Chromium (in the container, the preinstalled `chromium_headless_shell-1243` found through `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`). There is no default `executablePath`; `E2E_CHROMIUM_PATH` is an optional override (tested with `/opt/pw-browsers/chromium-1243/chrome-linux64/chrome`). `--no-sandbox` is added only when the process runs as root, as in the container |
| Configuration | `e2e/playwright.config.ts`: 1 worker, no retries, `baseURL` from `E2E_BASE_URL` (default `http://localhost:8080`; refused if not local unless `E2E_ALLOW_NON_LOCAL=1`), trace `retain-on-failure`; no default `executablePath`; `E2E_CHROMIUM_PATH` optional; `--no-sandbox` only as root |
| Test | `e2e/tests/create-task.spec.ts`, named "AC-10.1 · sign in, open a project, create a task and see it in the list" |
| Environment variables | `E2E_USER_EMAIL` (`ana@example.com`), `E2E_USER_PASSWORD` (the `MATRIZ_DEMO_PASSWORD` given to the seeder), `E2E_BASE_URL`, `E2E_ALLOW_NON_LOCAL` (set to `1` to allow a non-local base URL) |
| Database | Local PostgreSQL 16, database `matriz_test` owned by role `matriz`. Passwords are fictitious and set in the shell only |
| Start | Set `MATRIZ_DB_URL` (test database), `MATRIZ_DB_USER`, `MATRIZ_DB_PASSWORD` and `MATRIZ_DEMO_PASSWORD`; seed once on a fresh test database with `mvn spring-boot:run "-Dspring-boot.run.profiles=seed"` (37 new rows inserted); start `mvn spring-boot:run`; in `e2e/` run `npm install`, then `npx playwright test` |
| Warning | Do not run `mvn verify` between seeding and the E2E run. The Java tests overwrite the demo accounts' passwords with their own test password, and seeding again inserts 0 rows and does not restore them |
| Data | Demo user Ana (Project Manager, Acme Studio); project "Website redesign"; owner Ben Demo (Team Member); fixed due date 2026-12-01; status left at the default; unique title `E2E task <timestamp>-<random>`. Cleanup deletes only that task through the UI Delete button and its confirmation |
| Assertions | Status "Por fazer" (the UI label of To do, mapped in README.md). Priority is not asserted (F17 is out of scope) |
| Guard | The test refuses to run when the host of `E2E_BASE_URL` is not `localhost` or `127.0.0.1`, unless `E2E_ALLOW_NON_LOCAL=1`. It checks the host name only; it cannot identify the database (no UI or API contract exposes it) |

Selector strategy: roles, labels and visible text only; no `data-testid`, no CSS classes, no `waitForTimeout`; web-first auto-waiting assertions. Selectors used: labels Email and Password and button "Sign in"; heading level 1 "Dashboard"; button `/^Ana Demo/` (account menu); the banner with Acme Studio and Northwind Lab; link "Website redesign"; the project name as heading 1; heading 2 `/^Tasks/`; button "+ New task"; the form named "New task" with labels "Title (required)", "Owner", "Due date" and "Status"; button "Create task"; role `status` with `“<title>” was created.`; role `alert` count 0; the table row found by its title button, cells 0 to 3; button `Delete “<title>”`, dialog button "Delete task", and status `“<title>” was deleted.`.

### Flow results

| Step | Expected result | Observed | Status | Source |
|---|---|---|---|---|
| 1. Sign in as Ana | URL `#/dashboard`; heading "Dashboard"; "Ana Demo" in the account menu; Acme Studio and Northwind Lab in the top bar | As expected | Pass | FR-01; US-01; UI-A1; E-01; E-07 |
| 2. Open "Website redesign" | Project heading, Tasks section and "+ New task" visible | As expected | Pass | FR-08; FR-13; US-12; FR-05 |
| 3. Create the task | `“<title>” was created.` shown; no alert | As expected | Pass | AC-10.1; FR-09 to FR-12 |
| 4. Task in the list | Exact title, owner Ben Demo, due 2026-12-01, status Por fazer | As expected | Pass | AC-10.1; BR-11 |
| 5. Reload the page | The task is still listed | As expected | Pass | NFR-03 |
| 6. Cleanup through Delete | `“<title>” was deleted.` and the row is gone | As expected | Pass | — (test cleanup) |

### Runs

| Run | Command | Result | Database state after the run |
|---|---|---|---|
| Baseline (tester) | `mvn verify -q` with `MATRIZ_DB_URL` and `MATRIZ_TEST_DB_URL` set to `matriz_test` | Surefire reports: 323 tests, 0 failures, 0 errors, 0 skipped | 5 tasks |
| Development run (not counted) | `npx playwright test` | 1 passed | Not recorded |
| Tester run 1 | `npx playwright test` | 1 passed | 5 tasks; 0 stray (`task_id` not like `e0000000-%`) |
| Tester run 2 | `npx playwright test` | 1 passed (1.2 s) | 5 tasks; 0 stray |
| Independent run 1 (main session, freshly recreated `matriz_test`; seed inserted 37 new rows; `GET /` returned 200) | `npx playwright test` | 1 passed (1.6 s) | 5 tasks; 0 stray |
| Independent run 2 (main session) | `npx playwright test` | 1 passed (1.0 s) | 5 tasks; 0 stray |
| Tester, after the config change, run 1 (observed 2026-11-18) | `npx playwright test` with `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`, `E2E_CHROMIUM_PATH` unset | 1 passed (2.7 s), using the preinstalled `chromium_headless_shell-1243` | 5 tasks; 0 stray |
| Tester, after the config change, run 2 (observed 2026-11-18) | Same as run 1 | 1 passed (1.6 s) | 5 tasks; 0 stray |
| Tester, after the config change, run 3 (observed 2026-11-18) | `npx playwright test` with `E2E_CHROMIUM_PATH=/opt/pw-browsers/chromium-1243/chrome-linux64/chrome` | 1 passed (2.4 s) | 5 tasks; 0 stray |
| Tester, after the config change, run 4 (observed 2026-11-18; expected failure) | `npx playwright test` with neither `PLAYWRIGHT_BROWSERS_PATH` nor `E2E_CHROMIUM_PATH` set | Failed as expected: `browserType.launch: Executable doesn't exist at /root/.cache/ms-playwright/chromium_headless_shell-1243/...`. Environment limitation, not an application defect | 5 tasks; 0 stray |
| Tester, after the config change, install attempt (observed 2026-11-18) | `npx playwright install chromium` | Failed in this container: `Failed to download Chrome for Testing 153.0.8010.12 ... Download failure, code=1` (download blocked). Environment limitation evidence | Not applicable |
| Tester, failure check (a) (observed 2026-11-18; fresh `matriz_test`, seed 37 rows, `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`) | `npx playwright test` with the spec temporarily expecting due date 2026-12-02 | Failed as expected: `Expect "toHaveText"` on the due-date cell, unexpected value "2026-12-01". `trace.zip` written (430224 bytes). Cleanup deleted the task | 5 tasks; 0 stray |
| Tester, failure check (b) (observed 2026-11-18; same environment) | `npx playwright test` with the spec temporarily expecting status "Em curso" | Failed as expected, unexpected value "Por fazer". `trace.zip` written (424915 bytes) | 5 tasks; 0 stray |
| Tester, guard: default run (observed 2026-11-18) | `npx playwright test` | Passed | 5 tasks; 0 stray |
| Tester, guard: `E2E_BASE_URL=http://example.invalid:8080` (observed 2026-11-18) | `npx playwright test` | Failed fast with `E2E_BASE_URL host "example.invalid" is not localhost or 127.0.0.1; this test creates and deletes data. Set E2E_ALLOW_NON_LOCAL=1 to allow it.` and no navigation | Not recorded |
| Tester, guard: same with `E2E_ALLOW_NON_LOCAL=1` (observed 2026-11-18) | `npx playwright test` | Got past the guard and failed at `page.goto` with `net::ERR_NAME_NOT_RESOLVED` | Not recorded |
| Tester, two further normal runs (observed 2026-11-18) | `npx playwright test` | Both passed | 5 tasks; 0 stray |
| Tester, after R-02/R-03, run 1 (observed 2026-11-18; fresh `matriz_test`, seed 37 rows, `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`; spec after the review fixes: columns located by header text, project link scoped to the Projects region without `.first()`, `afterEach` re-opens the project and fails with `created task not found for cleanup: <title>` if the row is missing) | `npx playwright test` | 1 passed (2.0 s) | 5 tasks; 0 stray |
| Tester, after R-02/R-03, run 2 (observed 2026-11-18; same environment) | `npx playwright test` | 1 passed (1.4 s) | 5 tasks; 0 stray |
| Tester, after R-02/R-03, negative check (observed 2026-11-18; same environment; the due-date assertion was temporarily set to the literal `'2026-12-02'`, then reverted by hand and verified with `diff`) | `npx playwright test` | Failed as expected: Expected "2026-12-02", Received "2026-12-01". Cleanup still ran | 5 tasks; 0 stray |
| Type check after R-02/R-03 (observed 2026-11-18; run by the tester and by the main session) | `npx --no-install tsc --noEmit -p .` from `e2e/` | Exit code 0 | Not applicable |

A first negative attempt that changed the shared `DUE_DATE` constant still passed, because the form fill used the same constant. It was not a valid check and was reverted.

The temporary breaks in failure checks (a) and (b) were reverted (the spec was restored) and not committed.

The main session did not re-run `mvn verify`.

### Failures

No application defect and no test problem. Before the config change, none occurred. After it, two environment limitations were observed on 2026-11-18: run 4 (no browser configured) failed as expected, and `npx playwright install chromium` could not download the browser in this container (see Runs). `npm install` (with `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`) and the Maven downloads worked.

### Not verified

- The `retain-on-failure` trace was triggered and a `trace.zip` was produced, but its content was not opened or inspected.
- A hostname guard cannot prove which database the application uses.
- Browsers other than Chromium, and Windows.
- A run as a non-root user (the `--no-sandbox` branch for non-root is not exercised), and macOS.
- `npx playwright install chromium`: the download is blocked in this container, so the standard install route is not confirmed here.
- A type check of the spec with `@types/node`. A standalone `tsc` shows only the missing `@types/node` for `process`; it was not added.
- The `created task not found for cleanup` failure path of the `afterEach` was not triggered.
- A column reorder in the UI was not exercised.

### Documentation updates

- Applied (Edit A, 2026-11-18): the AC-10.1 wording in `docs/user-stories.md` now carries a clarification that the interface shows "Por fazer" for "To do", as in README.md.
- Recorded as [Q-18](#open-questions-and-documentation-gaps): US-01 and US-12 have no acceptance criteria, so the sign-in and project-detail expectations rest on FR-01, FR-08, FR-13, UI-A1 and README.md. Writing criteria remains a decision for the project decision-maker (KC-01).
- Still open: TE-01 to TE-03 remain unautomated.
