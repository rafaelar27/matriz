# Matriz — Test Plan: Tasks and Permissions

> **Status:** reviewed plan, written on 2026-11-17. Expected results come only from the documentation ([requirements.md](requirements.md), [user-stories.md](user-stories.md), [constraints.md](constraints.md), [database.md](database.md) and [openapi.yaml](../openapi.yaml)), never from the behavior of the implementation. No code was read to write this plan, and no test was written or run. Rules marked **prov.** are provisional until their pending decisions (PD-01, PD-03, PD-17, PD-20) are made, and the matching scenarios must change with them.

## Purpose and scope

**Purpose:** define verifiable scenarios for tasks and the permissions around them, so that tests can be written later from the documented requirements.

**In scope:** create, list, get, update (title, description, due date, owner, status), delete, validation (title, description, status, owner, due date, defaults, unknown fields), and access by users with and without permission, including direct API requests with changed identifiers. Requirements: FR-05, FR-09 to FR-13, FR-15, FR-18 (progress effects only), NFR-01, NFR-03, SP-01 to SP-04. Stories: US-10, US-11, US-14, US-19, US-25, US-26.

**Out of scope:** see [Scenarios intentionally excluded](#scenarios-intentionally-excluded).

## Test levels

| Level | Meaning |
|---|---|
| **Unit** | An isolated rule without database or authentication, for example title validation. Used only if the rule is isolated in code (Q-10). |
| **API** (Integration/API) | An HTTP request to the application with authentication and the database. Verifies status codes, bodies, persistence and permissions. |
| **E2E** (End-to-end) | Through the user interface. Used only where the acceptance criterion is about what the interface offers or says. |

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

| ID | Scenario | Preconditions | Input | Expected result | Level | Source |
|---|---|---|---|---|---|---|
| TC-01 | PM creates a task with every field | Ana signed in; Ben is a member of P1 | `POST /projects/P1/tasks` with title "Draft homepage text", description, owner Ben, due 2026-11-06 | 201. Body has project_id=P1, title, description, owner_member_id=Ben, owner_name, due_date, status `To do`, created_by=Ana. `Location` header present (value not asserted, Q-11) | API | AC-10.1; FR-09 to FR-12; BR-11; createTask |
| TC-02 ★ | A created task can be read back | TC-01 done; then Ben reads in a new request | `GET` the task; `GET` P1's task list; `GET /progress?project_id=P1` | 200 with the same fields as the 201 body. The task is in the list: `total` +1 and `status_counts` To do +1. Progress total +1, done unchanged | API | AC-10.1; FR-13; FR-18; NFR-03; A10 |
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
| TL-02 | Order, fields, counts | P1 has: due 11-09 "b"; due 11-02 "a"; no due "Beta"; no due "alpha" | `GET` list | Order: 11-02, 11-09, then `alpha`, `Beta`. Each item has the Task fields. `status_counts` has the three statuses in order To do, In progress, Done | API | FR-13; decision log 2026-11-12; A9 |
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
| A10 | Persistence is proven by reading in a separate request; no restart check. | NFR-03 gives no method | TC-02 | Accepted |
| A11 | Comments and assignees are seeded and cascade-checked. Notifications cannot be created through the API yet, so they are seeded directly or left out. | AC-26.3; createTask "Not yet implemented" | TD-02 | Accepted |

## Open questions and documentation gaps

These stay open until the project decision-maker answers them (KC-01). No scenario guesses an answer.

| ID | Question or gap | Related IDs | Effect on the plan |
|---|---|---|---|
| Q-03 | What does a deactivated member get, 403 or 404? `Forbidden` lists "deactivated"; `ProjectNotFound` implies "not in the workspace". | SP-04, FR-03 | TC-08 and TA-07 assert only "refused, no data" |
| Q-07 | Does a title of tabs, newlines or non-breaking spaces count as "only spaces"? How are characters counted for non-ASCII text? | FR-09, BR-10 | Untested |
| Q-08 | What happens with a wrong JSON type (for example `title: 123`) and with a due date in the past on creation? | API-A2, FR-11 | Untested |
| Q-09 | What does a malformed id in the path return, 404 or 422? | API-A4 | Untested |
| Q-10 | Is the title rule an isolated unit? | FR-09 | Decides whether TV-09 exists at Unit level |
| Q-11 | What is the `Location` header value? The `openapi.yaml` example was corrected on 2026-11-17 to a project-scoped path, but the format is not stated as a rule. | createTask | TC-01 checks presence only |
| Q-12 | Are baseline fixtures built per test or taken from the demo seed? Deactivating Eli and demoting a creator need direct setup. | — | Test-tooling choice for the programmer |

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
