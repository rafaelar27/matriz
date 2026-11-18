# Matriz — QA Verification Campaign

> **Status:** QA verification campaign of 2026-11-16, approved by the project decision-maker on 2026-11-16. Requirement IDs are defined in [requirements.md](requirements.md), user stories, acceptance criteria and edge cases in [user-stories.md](user-stories.md), and the operations in [openapi.yaml](../openapi.yaml). Increment status is in [implementation-plan.md](implementation-plan.md#verification-campaign-2026-11-16). All demo data and accounts named here are fictitious. No password or other secret is recorded in this file.

## Environment and setup

| Item | Value |
|---|---|
| Operating system | Windows 11 |
| Java | 21.0.11 (`java -version`) |
| Maven | Apache Maven 3.9.16 (`mvn -v`) |
| PostgreSQL | 18.4 on `localhost:5432` (`pg_isready`: "accepting connections"; `select version()`) |
| Code | Branch `feat/ui-signin`, base commit `138cb2c`; [openapi.yaml](../openapi.yaml) 0.7.0-draft |
| Automated tests database | `matriz_test` (profile `test`, demo data loaded per test and rolled back). `MATRIZ_DB_URL` was set in the shell to `jdbc:postgresql://localhost:5432/matriz_test`, so `DatabaseSetupTest` also used it. |
| Manual checks database | `matriz_verify`, a scratch database created for this campaign (approved, Q2) as the `matriz` role with `CREATE DATABASE matriz_verify OWNER matriz` (psql, connected to `postgres`). Used for every manual check and restart (approved, Q3). It still exists; dropping it needs separate approval. |
| Development database | `matriz`: never used in this campaign. |
| Application under test | Port 8091 (`MATRIZ_PORT=8091`, `MATRIZ_DB_URL` pointing to `matriz_verify`, `MATRIZ_DEMO_PASSWORD` set in the shell to a freshly generated fictitious value, never written to the repository). Run from a copy of the packaged jar (`java -jar`); after the B-01 fix, with `mvn spring-boot:run` (from `target/classes`), because the jar was locked (see [Limitations](#limitations-and-items-not-executed)). |
| Browser | Claude desktop built-in browser (Chromium) at `http://localhost:8091` |
| API and SQL tools | curl (Git Bash) with cookie jars against `http://localhost:8091`; psql on `matriz_verify` |

## Commands executed and results

| # | Command | Result |
|---|---|---|
| 1 | `mvn -q -DskipTests package` | Jar built (`target/matriz-0.1.0-SNAPSHOT.jar`). |
| 2 | `java -jar matriz.jar --spring.profiles.active=seed` on `matriz_verify`, run twice | First run: "Successfully applied 4 migrations to schema public, now at version v4" and "Demo data: 37 new rows inserted". Second run: "Schema public is up to date" and "Demo data: 0 new rows inserted". |
| 3 | `java -jar matriz.jar` (start 1) | "Schema public is up to date", "Started MatrizApplication in 8.657 seconds". |
| 4 | `mvn test -Dtest=VerificationApiTest`, then `mvn verify` (tester agent, `matriz_test`, before the B-01 fix) | `VerificationApiTest`: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS. `mvn verify`: Tests run: 140, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS. Confirmed by the main session from `target/surefire-reports` (140/0/0 in total; `VerificationApiTest` 12/0/0). |
| 5 | Application stopped and started again (start 2) | "Schema public is up to date", "Started MatrizApplication in 9.548 seconds". |
| 6 | `mvn -DskipTests package` (after the B-01 fix) | BUILD FAILURE at `spring-boot:repackage`: "Unable to rename target/matriz-0.1.0-SNAPSHOT.jar". The jar is locked by a Java process (PID 33068, `java -jar target/matriz-0.1.0-SNAPSHOT.jar`) started at 14:47, before this campaign; it was not started by the campaign and was left running. |
| 7 | `mvn -q spring-boot:run` on `matriz_verify` (start 3) | "Database: jdbc:postgresql://localhost:5432/matriz_verify", "Schema public is up to date", "Started MatrizApplication in 7.869 seconds". `curl http://localhost:8091/js/app.js` contains the fix line. |
| 8 | `mvn verify -Dspring-boot.repackage.skip=true` (final run, `matriz_test`, scratch application stopped) | Tests run: 140, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS; Flyway log "Database: jdbc:postgresql://localhost:5432/matriz_test". Per class: `AssignmentApiTest` 8, `AuthenticationTest` 10, `ClientConfigApiTest` 4, `CommentApiTest` 7, `CsrfCookieTest` 1, `DatabaseSetupTest` 3, `ProjectApiTest` 42, `ClientConfigControllerTest` 16, `TaskAccessTest` 21, `TaskApiTest` 16, `VerificationApiTest` 12. The only difference from `mvn verify` as written in the README is the skipped repackage step (command 6). |
| 9 | `mvn -q verify` (reviewer agent) | 140 tests passed; the build failed at repackage because of the same locked jar. |
| 10 | `mvn -q spring-boot:run` on `matriz_verify` (start 4, follow-up role checks) | "Database: jdbc:postgresql://localhost:5432/matriz_verify", "Schema public is up to date", "Started MatrizApplication in 5.238 seconds". Stopped after the checks. |

## Scenarios

**Type:** *Automated* means a JUnit test in the suite; *Manual* means curl, SQL or browser checks on `matriz_verify`; *Both* means both were done.

| Case | Scenario | Type | Expected result | Observed result | Evidence | Status |
|---|---|---|---|---|---|---|
| 01 | Access: sign-in, refused sign-in, no session, CSRF, sign-out, restart | Both | Valid credentials sign in and return the profile with no password or hash; a wrong password, an unknown email and empty fields get the same generic 401 `invalid_credentials`; requests without a session get 401 `not_authenticated`; a change without the CSRF header gets 403 `csrf_invalid`; after sign-out or a restart the old cookie gets 401 `session_expired`; the interface keeps the email, clears the password, and returns to the same screen after signing in again (US-01, FR-01, SP-01, TD-03, UI-A7; `signIn`, `getCurrentProfile`, `signOut`, `getCsrfToken`). | Valid sign-in 200 with `account_id`, name, email and memberships, no password or hash field. Wrong password, unknown email, `{"email":"","password":""}`, an empty password and `{}`: identical 401 "The email or password is incorrect." `GET /projects` and `GET /projects/{id}/tasks` without a session: 401 `not_authenticated` "Sign in to continue." `POST /projects` without `X-XSRF-TOKEN`: 403 `csrf_invalid`. Sign-out 204; old cookie on `GET /auth/me`: 401 `session_expired` "Your session has ended. Sign in again."; the same after a restart. Browser: wrong password showed the message, kept the email and emptied the password field; after a restart, opening a project address led to sign-in with "Your session has ended. Sign in again." and back to the same project page. | curl; browser; `AuthenticationTest` | Pass |
| 02 | Projects: create, search, edit, refused actions, other workspace, delete | Both | A Project Manager creates a project and is its owner; search matches part of the name ignoring case, "zzz" gives no result, spaces only list every project; an edited due date shows in the list; Team Members, Viewers and Workspace Administrators get 403 (PD-03 provisional); another workspace gets 404; deleting removes the project with its tasks, comments, notifications and project members (US-06, US-24 AC-24.1, US-27 AC-27.2, AC-27.3, US-28 AC-28.1, AC-28.2, FR-06, FR-08, NFR-01; `createProject`, `listProjects`, `getProject`, `updateProject`, `deleteProject`). | Ana created "QA verification project" (2026-11-20 to 2026-12-15): 201, owner Ana. `q="QA VERIF"`: total 1; `q=zzz`: no items, total 0; `q="   "`: total 4. Due date 2026-12-22: 200, shown in the list. Ben `PATCH`: 403 "Only Project Managers can create, edit or delete projects."; Vera `DELETE`: 403 "Viewers have read-only access."; Carla `DELETE`: 403 "Workspace Administrators cannot manage projects (pending decision PD-03)."; Ben `POST`: 403. Dan `GET`/`PATCH`/`DELETE`: 404 "The project was not found."; Dan's search "QA": total 0. Delete of the project with 1 task, 1 comment and 1 project member: 204, then 404; SQL counts of project, task, comment, notification, project_member and task_assignee rows for those ids all 0; orphan scan 0/0/0. | curl; SQL; `ProjectApiTest` | Pass |
| 03 | Task creation and persistence | Both | A created task appears in the task list with its owner and due date, the counts and progress update without reloading the page, and the task, its comments and assignees are unchanged after a backend restart (US-10 AC-10.1, US-17, FR-09, FR-13, FR-18, NFR-03; `createTask`, `listTasks`, `getTask`). | Browser (Ana, Website redesign): "QA browser task", owner Ben, due 2026-12-05: `POST` 201, "“QA browser task” was created.", the row appeared, Por fazer 1 → 2, progress "1 of 3" → "1 of 4 tasks done"; a page marker set before creating was still present (no reload). SQL snapshot of the campaign's tasks, comments and assignees before stopping and after start 2: identical. After the restart, Vera's `GET` of the task: 200 with the same title, description, owner Ben, due date 2026-12-05, To do and creator Ana; comments and assignees read back; the browser listed the task after signing in. | `VerificationApiTest.case03CreatedTaskIsReadBackEverywhere`; browser; SQL; curl | Pass |
| 04 | Validation of titles, comments and project names | Both | An empty, spaces-only or missing title, or one outside 3 to 150 characters after trimming, is refused with a message and nothing is saved, on create and edit; a missing owner, an impossible date and a status other than the three values are refused; an empty or spaces-only comment, or one over 2000 characters, is refused and nothing is added; a missing or spaces-only project name is refused (US-10 AC-10.2, AC-10.5, US-25 AC-25.1, AC-25.2, US-15 AC-15.2, EC-08, FR-06, FR-09, FR-12, FR-16; `createTask`, `updateTask`, `addTaskComment`, `createProject`: 422 `validation_failed`). | `createTask`: title `""`, `"   "`, `"\t \n "`, `null` and missing: 422 "A title is required."; `"  ab  "`: 422 "The title must be 3 to 150 characters long."; owner missing: 422 "An owner is required."; due date 2026-02-30: 422 "The value has the wrong format."; status `DONE` and `Concluída` (UTF-8): 422 on `status`; task count 5 → 5. `updateTask` title `"   "` and `""`: 422, stored title unchanged. Comments `""`, `"    "`, `"\n\t "`, `null`: 422 "A comment cannot be empty."; comment count 3 → 3. Project name `"   "`: 422; missing: 422 "A name is required." Browser: a spaces-only task title and a spaces-only comment were refused at the field with no request sent; a 2001-character comment (maxlength removed) got "A comment can be at most 2000 characters long." with no request. `Concluída` sent from Git Bash with non-UTF-8 bytes gave 400 `malformed_request`, the contract's response to an unreadable body: a test-tool artefact, not a defect. | `VerificationApiTest` `case04*` (6 tests); curl; SQL; browser | Pass |
| 05 | Status change agrees everywhere | Both | The task's owner changes its status; the task, the status filter, the counts per status and the project progress all show the new status; a non-owner Project Manager (PD-01 provisional) and a Viewer get 403 and nothing changes (US-11 AC-11.1, AC-11.3, US-17 AC-17.2, FR-12, FR-15, FR-18; `updateTask`, `listTasks`, `getProjectProgress`; the operation was called `updateTaskStatus` until it was removed from the contract on 2026-11-17). The "My tasks" part of AC-11.1 has no API operation yet (FR-14). | Before: filter Done total 1; counts To do 1 / In progress 1 / Done 1; progress 1 of 3. Ben (owner) set the task to Done: 200; filter Done total 2; counts 1/0/2; progress 2 of 3; `GET` showed Done; SQL `DONE`. Ana (Project Manager, not the owner): 403 "Only the task's owner can change its status (pending decision PD-01)."; Vera: 403; SQL still `DONE`. Ben then set it back to In progress (200). | `VerificationApiTest.case05StatusChangesAgreeEverywhere`; curl; SQL | Pass (2026-11-12 run, through the former `PATCH /tasks/{id}`). Re-verified 2026-11-17 after INC-08: `VerificationApiTest.case05StatusChangesAgreeEverywhere` now calls the project-scoped `PATCH /projects/{project_id}/tasks/{task_id}` (`updateTask`) and passes (`mvn verify` on `matriz_test`, 0 failures). Automated check only; no manual or browser check was repeated. |
| 05b | Task priority | — | Not applicable: task priority is F17, outside the initial scope (decision of 2026-11-12), and not in the contract. | A `PATCH` with `priority` got 422 "This field cannot be sent." No priority behaviour was tested. | curl | Not executed (F17 outside the MVP) |
| 06 | Assignees | Both | Several project members can be assigned and the owner is unchanged; a duplicate in one request or an existing assignee is refused with 409 and listed once; a member who is not in the project is refused (422); only Project Managers assign (PD-01 provisional); removing one keeps the others; of simultaneous identical assignments one succeeds and the database refuses the duplicate (US-29 AC-29.1 to AC-29.5, EC-10, FR-07, FR-22, BR-22; `assignTask`, `unassignTask`, `listTaskAssignees`). | `[Ben, Ben]`: 409 "Ben Demo appears more than once in the request.", 0 rows. `[Ben, Vera]`: 201, both listed; Ben again: 409 "Ben Demo is already assigned to this task."; SQL 1 row each. Carla (Workspace Administrator, not a project member): 422 "Only active members of this project can be assigned."; Ben assigning: 403 (PD-01). `DELETE` Ben: 204, only Vera listed, owner unchanged (Ana). 5 parallel identical requests for Ben: 1 × 201, 4 × 409; SQL 1 row; the log shows the database refused the duplicate (`task_assignee_pk`), returned as 409. Browser: Ben and Vera assigned, "Assigned.", still listed after a restart (SQL and API). | `VerificationApiTest.case06DuplicateAssigneesAreRefused`; curl; SQL; browser | Pass |
| 07 | Comments: author and date from the server | Both | A comment is trimmed; its author and date are set by Matriz; a sent author or date is refused (422); every member can read; a Viewer cannot comment (US-15 AC-15.1, AC-15.3, AC-15.4, FR-16; `addTaskComment`, `listTaskComments`). | Ana posted "  QA comment from Ana  ": 201, body trimmed, `author_member_id` Ana, `author_name` "Ana Demo", `created_at` set by the server; Vera's list showed it; SQL author is Ana's member id. `author_member_id` sent: 422 "This field cannot be sent."; `created_at` sent: 422; Vera `POST`: 403. Browser: "QA browser comment" shown as "Ana Demo · 16 Nov 2026, 19:48", still listed after a restart. See [O-2](#other-findings-not-fixed) for the date precision. | `VerificationApiTest.case07CommentAuthorAndDateComeFromTheServer`; curl; SQL; browser | Pass |
| 08 | Authorization across and inside workspaces | Both | A member of another workspace gets 404 for every project, task, comment, assignee and member operation, and nothing changes; `getProjectProgress` leaves out projects the caller cannot see (200); a task reached through another project id is not found; inside the workspace, refused actions get 403 and change nothing (NFR-01, SP-02, API-A3, US-19, US-26 AC-26.2, FR-04, FR-05, FR-09, BR-12, PD-01 and PD-17 provisional; the task, comment and assignee operations, `getProjectProgress`, `listWorkspaceMembers`). | Dan on Acme ids (task `GET`/`PATCH`/`DELETE` through its own and another project id, comments `GET` and `POST`, assignees `GET`/`DELETE`/`POST`, `GET /workspaces/{Acme}/members`): all 404 `not_found`. `GET /progress?project_id=` an Acme project: 200 `[]`. Ana `PATCH` of an Acme task through a Northwind project id: 404. Ben: editing a task he does not own 403 (PD-01); deleting a task he did not create 403 "Only the member who created a task can delete it."; reassigning his task 403; creating a task with owner Ana 403. md5 snapshot of the task, comment, task_assignee and project tables: unchanged. | `VerificationApiTest.case08OtherWorkspaceIdsAreNotFoundAndChangeNothing` (20 requests, 5-table snapshot), `case08RefusedActionsInsideTheWorkspaceChangeNothing`; curl; SQL | Pass (2026-11-12 run, which also sent the former `PATCH /tasks/{id}`). Re-verified 2026-11-17 after INC-08: `VerificationApiTest.case08OtherWorkspaceIdsAreNotFoundAndChangeNothing` and `case08RefusedActionsInsideTheWorkspaceChangeNothing` now use the project-scoped `PATCH /projects/{project_id}/tasks/{task_id}` (`updateTask`) and pass (`mvn verify` on `matriz_test`, 0 failures). Automated check only; no manual or browser check was repeated. |
| 09 | Deletion and error display | Both | Deleting a project or task leaves no orphan rows (US-26 AC-26.3, US-27 AC-27.2, FR-06, FR-09; `deleteProject`, `deleteTask`); after a refused or failed save, the form shows the error, keeps the entered values and shows no success message (contract error responses; scenario 09 of the approved plan). | Deletion: see case 02. Task form with a non-member owner (Carla): 422, "Check the highlighted fields." and "The owner must be a member of this project." at Owner, values kept, no success message. Comment box: a simulated 403 and a simulated network failure showed their messages at the field and kept the text. Task edit form, before the fix: a simulated 403 and network failure showed the error in the form banner and kept the values, but an earlier "“QA browser task” was created." notice stayed visible above the form: **failed**, recorded as [B-01](#bugs-found). After the fix: only the error is shown and the values are kept; the success path still shows its notice. | `ProjectApiTest.deletingAProjectRemovesItsTasksCommentsNotificationsAndMembers`, `TaskApiTest.deletingATaskRemovesItsCommentsAndNotificationsAndUpdatesTheCounts`, `AssignmentApiTest.deletingATaskDeletesItsAssignments`; browser (scripted checks) | Pass after the B-01 fix (failed before it) |

## Role checks in the browser

Follow-up on 2026-11-16, Manual. Sign in on `matriz_verify` (command 10) as each role, open "Website redesign", then open the details of "Approve page layout" (owner and creator: Ana). The visible controls were read from the page.

| Role (account) | Expected (ID) | Observed | Status |
|---|---|---|---|
| Team Member (Ben) | No Edit project or Delete project (AC-27.3). Delete only on tasks he created (AC-26.2). Edit and the status list only on tasks he owns (PD-01, provisional). Assignees visible but no assign or remove (AC-29.5). As a project member he may comment (AC-15.4, provisional). | Project area: "← Projects" and "+ New task" only. Rows: "Review homepage images" (owner Ben, creator Ana): Edit and status list, no Delete. "Publish new homepage" (owner and creator Ben): Edit, Delete and status list. "Approve page layout": no actions, no status list. "QA browser task" (owner Ben, creator Ana): Edit and status list. Details: assignees Ben and Vera listed, no checkboxes, no Assign or Remove; comment box and "Post comment" shown. | Pass |
| Viewer (Vera) | No option to create, edit, delete or archive projects or tasks, change a status or owner, or comment (AC-19.2, FR-05). Comments readable (AC-15.4). No assign or remove (AC-29.5). | Project area: "← Projects" only. Every row: no actions, no status list. Details: "Close" only, no checkboxes, no comment box; the comment by Ana is listed. | Pass |
| Workspace Administrator (Carla) | No project or task changes and no comment (AC-27.3, AC-15.4, AC-29.5; PD-03, provisional). | Same as Vera: "← Projects" only, no row actions, no status list, details "Close" only, comments readable, no comment box. | Pass |

## Automated tests

All tests were run with `mvn verify -Dspring-boot.repackage.skip=true` and `MATRIZ_DB_URL` set to `matriz_test` (command 8). **Required trio:** validation = the `case04*` tests; authorization = the `case08*` tests; create and persistence = `case03CreatedTaskIsReadBackEverywhere`.

**Later runs (2026-11-17, INC-08, local PostgreSQL 16, database `matriz_test`):** `mvn verify` gave 145 tests, 0 failures before INC-08 (baseline), and 146 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS after it (one regression test, `TaskAccessTest.unscopedStatusRouteIsGone`, added). Later test-coverage work, separate from INC-08, raised the suite to 170 tests, and two further groups of tests (TU-06, invalid title, description and owner on edit; TA-05, a task of another workspace reached through the caller's own project; four tests in `TaskApiTest`) raised it to 174 tests on 2026-11-17. All runs: 0 failures, 0 errors, 0 skipped, BUILD SUCCESS on `matriz_test` (the tester ran the 174-test suite twice and the main session re-ran it independently); see [test-plan.md](test-plan.md). `VerificationApiTest` still has 12 tests, all passing on the project-scoped route. The 140 tests and the per-class counts above and in the table are those of the 2026-11-16 campaign run (command 8) and are kept as the record of that run.

| Test | Related case and US | Command | Result |
|---|---|---|---|
| `VerificationApiTest.case03CreatedTaskIsReadBackEverywhere` **(trio: create and persistence)** | Case 03; US-10 (AC-10.1), FR-09, FR-13, NFR-03 | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case04CreateRefusesInvalidTitles` **(trio: validation)** | Case 04; US-10 (AC-10.2, AC-10.5) | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case04CreateAcceptsBoundaryTitlesTrimmed` **(trio: validation)** | Case 04; US-10 (AC-10.5) | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case04UpdateRefusesInvalidTitles` **(trio: validation)** | Case 04; US-25 (AC-25.1, AC-25.2), AC-10.5 | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case04UpdateAcceptsBoundaryTitlesTrimmed` **(trio: validation)** | Case 04; US-25 (AC-25.2) | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case04CommentRefusesInvalidBodies` **(trio: validation)** | Case 04; US-15 (AC-15.2), EC-08 | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case04CommentAcceptsBoundaryBodies` **(trio: validation)** | Case 04; US-15 (AC-15.2) | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case05StatusChangesAgreeEverywhere` | Case 05; US-11 (AC-11.1), US-17 (AC-17.2), FR-15 | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case06DuplicateAssigneesAreRefused` | Case 06; US-29 (AC-29.1, AC-29.3, AC-29.4), BR-22 | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case07CommentAuthorAndDateComeFromTheServer` | Case 07; US-15 (AC-15.1, AC-15.3) | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case08OtherWorkspaceIdsAreNotFoundAndChangeNothing` **(trio: authorization)** | Case 08; NFR-01, SP-02, API-A3 | `mvn verify` (command 8) | Pass |
| `VerificationApiTest.case08RefusedActionsInsideTheWorkspaceChangeNothing` **(trio: authorization)** | Case 08; US-19 (FR-05), FR-04, BR-12, PD-01 and PD-17 provisional | `mvn verify` (command 8) | Pass |
| `AuthenticationTest` (existing, 10 tests) | Case 01; US-01 | `mvn verify` (command 8) | Pass |
| `ProjectApiTest` (existing, 42 tests) | Cases 02 and 09; US-06, US-24, US-27, US-28 | `mvn verify` (command 8) | Pass |
| `TaskApiTest` (existing, 16 tests) | Case 09 (task deletion), and the task operations in general; US-10, US-11, US-25, US-26 | `mvn verify` (command 8) | Pass |
| `TaskAccessTest` (existing, 21 tests) | Task access rules (INC-02); US-10, US-11, US-19 | `mvn verify` (command 8) | Pass |
| `AssignmentApiTest` (existing, 8 tests) | Case 09 (assignments deleted with the task), and the assignee operations in general; US-29 | `mvn verify` (command 8) | Pass |
| `CommentApiTest` (existing, 7 tests) | Comment operations (INC-05); US-15 | `mvn verify` (command 8) | Pass |

## Bugs found

| ID | Description | Reproduction steps | Evidence | Root cause | Fix | Regression test | Status |
|---|---|---|---|---|---|---|---|
| B-01 | On a project's Tasks section, a success notice from an earlier action ("“X” was created.") stays visible above the task form when a later save of that form fails, so a success message is shown next to an API error (case 09). | 1) Sign in as ana@example.com. 2) Open "Trade fair stand". 3) + New task "QA stale notice task", owner Ana, Create task: "“QA stale notice task” was created." 4) Edit on that row. 5) Save a change the server refuses. Reproduced with a real 422 (owner value emptied), a real 404 (task deleted by another session before saving), a simulated 403 and a simulated network failure. | Before the fix, the visible status and alert texts after the failed save were "“QA stale notice task” was created." and "Check the highlighted fields."; the network showed `PATCH` 422; a screenshot showed the success line directly above the "Edit task" form and its error. The same with the real 404: "“QA regression B-01 task” was created." and "This task or project no longer exists." | `src/main/resources/static/js/app.js`: the task section keeps one section-level `notice`, written only by `say`. The task form's submit handler clears its own banner and field errors but never `notice`, which is only replaced by the next success message. | Option A, approved by the project decision-maker on 2026-11-16: one line added in the task form's submit handler, after `banner.replaceChildren();`, that clears `notice` (`app.js`, line 1098; 1 insertion). Option B (also clearing it in the comment and assignee areas) was not chosen. | No JavaScript test tool exists in the stack, and adding one was not approved, so the regression check is a **scripted manual browser check**, not an automated test. After the fix (page fully reloaded, `app.js` served with the fix): create "QA regression B-01 task 2", Edit, delete the task through the API (204), Save: `PATCH` 404 and only "This task or project no longer exists." is shown, with the typed title and description kept. The success path still works: an edit of "QA regression B-01 task 3" shows "“QA regression B-01 task 3 saved” was saved." The reviewer agent reviewed the fix: limited and correct, form data kept, other `say` paths unaffected, Option B respected. Full suite after the fix: 140 tests, 0 failures (command 8). | Fixed (verified manually; no automated regression test) |

## Other findings (not fixed)

| ID | Finding | Severity | Status |
|---|---|---|---|
| O-1 | **FR-19 overdue marking is not built.** "Review homepage images" (due 2026-11-04, In progress; checked on 2026-11-16) shows no overdue label in the task list, its row has no marker, and the API has no overdue field; [openapi.yaml](../openapi.yaml) only says that the overdue state is derived, and no implementation was found. The README also states that FR-14, FR-17, FR-20 and FR-21, and member management (FR-03, FR-04), have no API operations yet. The claim that "all MVP features are implemented end to end" is therefore **not accurate**. Outside the nine scenarios. | Needs a decision | Reported |
| O-2 | A comment's `created_at` has 7 decimal digits in the `POST` response (`…001416800Z`) and microseconds in the stored and listed value (`…001417Z`). | Low | Reported |
| O-3 | A page script that overwrote the `XSRF-TOKEN` cookie and sent the same value in the header was accepted (comment 201). This is expected for the configured double-submit CSRF cookie (`CookieCsrfTokenRepository.withHttpOnlyFalse()`, `SecurityConfig`): a script on the page can already read the token, so it is not a bypass. It is not exploitable cross-site, because the session cookie is SameSite=Lax and no CORS is configured. Security audit of 2026-11-16 (Pass) recorded three related Low findings: S-01, same-site cookie injection (sibling subdomain, plain HTTP, another localhost app) could set a known token, and the only reachable write is a forced sign-out; S-02, behind a TLS-terminating proxy the `XSRF-TOKEN` cookie would likely lack Secure; S-03, no test of a mismatched cookie/header pair or of the cookie attributes. S-01 and S-02 need a decision under TD-03. | Low | Reviewed by the security-auditor agent on 2026-11-16 |
| O-4 | `PATCH /projects/` (no id) returns Spring's default error body instead of the API's Error schema. The path is not in the contract. | Low | Reported |
| O-5 | The tester's brief first expected 404 for `/progress` with another workspace's project; the contract (`getProjectProgress`) says 200 with that project left out. The application and the tests follow the contract. | — | No change needed |

## Review findings

| ID | Severity | Finding | Outcome |
|---|---|---|---|
| R-01 | Major | The documentation was not updated: the README test count (128 → 140), and the campaign was not recorded. | Addressed by this file and the [implementation plan](implementation-plan.md#verification-campaign-2026-11-16) entry. The README change is proposed, not applied. |
| R-02 | Minor | The Javadoc of `VerificationApiTest` cites `docs/verification.md`, which did not exist. | Resolved by creating this file. |
| R-03 | Minor | After the fix, saving a task edit with no change closes the form with no message (before, a stale notice could remain). No documented rule covers this; the project form shows "Changes saved." (QC1). | Open question (see below). |

## Limitations and items not executed

| Item | Status | Reason |
|---|---|---|
| Priority part of case 05 (05b) | Not executed | Task priority is F17, outside the initial scope (decision of 2026-11-12), and not in the contract. |
| Automated regression test for B-01 | Not executed | No JavaScript test tool is in the stack, and adding one was not approved. Replaced by a scripted manual browser check. |
| `mvn verify` exactly as in the README | Not executed | The repackage step could not run because `target/matriz-0.1.0-SNAPSHOT.jar` is locked by PID 33068, which this campaign did not start and left running. `mvn verify -Dspring-boot.repackage.skip=true` was run instead; the tests are not affected. |
| Session idle expiry | Not executed | Not re-checked; covered by `AuthenticationTest.anEndedOrExpiredSessionIdGetsSessionExpired` and the INC-02 check (AC-INC02.4). |
| Screen reader and keyboard-only checks | Not executed | Not in the campaign's scope. |
| Physical clicks in the browser | Limitation | Where the browser pane did not draw, checks used scripted page input and clicks instead of physical clicks. |
| Scratch database `matriz_verify` | Limitation | It still holds the campaign's fictitious rows; dropping it needs approval. |
| Manual restart check, 2026-11-17 (INC-08, AC-INC08.14, test-plan A10, NFR-03) | Executed (manual, not an automated test) | See the paragraph below. |

**Manual restart check, 2026-11-17 (INC-08).** Done by the main session on the scratch database `matriz_verify` (not the campaign database of the same name described above: a separate database in the Linux container, created and dropped on 2026-11-17; separate from `matriz_test`; migrations V1 to V4 applied, demo data seeded; the application ran locally on a high port and was stopped afterwards). It is a manual check, not an automated test; it relates to INC-08 AC-INC08.14, test-plan A10 and NFR-03. *API level:* Ben (owner) changed the seeded task "Review homepage images" from In progress to Done through the project-scoped `PATCH /projects/{project_id}/tasks/{task_id}`: 200, a fresh `GET` showed Done, database read-back `DONE`. Ana created the task "Restart check task" (201). The application was stopped (database still `DONE`) and started again (migrations up to date). Ben's old session then got 401 `session_expired`; after signing in again, the `GET` showed Done, the created task was still there with its title, due date and owner, the list counts were total 4 (To do 2, In progress 0, Done 2) and matched the database, and the removed `PATCH /tasks/{id}` returned 404 with the database unchanged. *Interface level:* a throwaway headless-browser script (Playwright, not a committed test) signed in as Ben, changed the status select to "Em curso" (select showed In progress, database `IN_PROGRESS`), reloaded the page (still In progress); after an application restart the script signed in again and opened the project (still In progress, database `IN_PROGRESS`). The script stood in for a person using a browser; **no human browser session was performed**. The application was stopped afterwards; `matriz_verify` was dropped on 2026-11-17, with the human's approval, after the check. The case table above is unchanged.

## AI-assisted development

Three examples of how AI suggestions were handled. The AI tools named are those recorded in the repository: Claude Code (the `Co-Authored-By` lines of the commits and the project agents in `.claude/agents/`) and the Codex review on pull request #1. Details the repository cannot show are marked [TO CONFIRM BY THE PROGRAMMER].

| # | Request goal | Programmer's decision | Verification method |
|---|---|---|---|
| AI-1 | Fix B-01: a success notice from an earlier action stayed visible next to the error of a failed task save (case 09). Two fixes were put to the programmer (proposed by the AI session that ran the campaign: [TO CONFIRM BY THE PROGRAMMER]): Option A, clear the notice in the task form's submit handler only; Option B, also clear it in the comment and assignee areas. | **Adapted:** Option A was approved on 2026-11-16 and Option B was not chosen ([B-01](#bugs-found); commit `9529c3a`). Reason for not choosing Option B: [TO CONFIRM BY THE PROGRAMMER]. | Scripted manual browser check (a real 404 after the fix showed only the error and kept the typed values; the success path still showed its notice); the reviewer agent reviewed the one-line fix; full suite 140 tests, 0 failures. No automated test, because no JavaScript test tool is in the stack. |
| AI-2 | Create the project agents (planner, database, tester, reviewer, security-auditor, doc-keeper) with Claude Code (commits `f438c43`, `b07fe4e`, pull request #1). | **Corrected:** the Codex review of PR #1 found that the agents' documented test command, `mvn verify`, could apply a pending migration to the development database, because `DatabaseSetupTest` does not use the test profile. The finding was confirmed and the agent definitions were changed so that the test command runs only with `MATRIZ_DB_URL` pointing at the test database (commit `5ab9ac3`, reply on the review comment). Whether the programmer checked the finding before the fix, or only approved it: [TO CONFIRM BY THE PROGRAMMER]. | Code reading: `DatabaseSetupTest` has no `@ActiveProfiles("test")`; review comment on `.claude/agents/reviewer.md`. Since then the agents' test runs log `Database: jdbc:postgresql://localhost:5432/matriz_test` (for example the release review runs of 2026-11-16). |
| AI-3 | Release review: propose at most one behaviour-preserving refactoring. The AI proposed moving the duplicated page and size parsing of `ProjectController` and `TaskController` into `shared/PageParams`, and itself set aside a fallback error handler (it changes responses) and moving the progress visibility check into `AccessPolicy` (access control). | **Accepted as proposed:** the plan was approved by the project decision-maker on 2026-11-16 and committed alone (`e7dfaad`); see the [status log](implementation-plan.md#status-log). | `mvn verify` (repackage skipped, jar locked) on `matriz_test` before and after: 140 tests, 0 failures, 0 errors both times; the reviewer agent compared the removed code with the new helper line by line and approved with no findings. |

## Open questions

1. **R-03:** should saving a task edit with no change show a message, as the project form shows "Changes saved." (QC1)?
2. **Scratch database:** may `matriz_verify` be dropped? This is a destructive action and needs approval (C1).
3. **Locked jar:** PID 33068 locks `target/matriz-0.1.0-SNAPSHOT.jar`. Once it is stopped, a plain `mvn verify`, as written in the README, should be run again.
4. **O-3:** ~~should the security-auditor agent review the CSRF cookie behaviour?~~ Answered 2026-11-16: reviewed, see O-3.
5. **FR-19 and the other unbuilt FRs (O-1):** what is decided for FR-19 overdue marking, and for FR-03, FR-04, FR-14, FR-17, FR-20 and FR-21, which have no API operations yet?

## Docker Compose

> **Status:** run on 2026-11-18 in a Linux cloud container (Docker Engine 29.10.0 started by hand with `dockerd`, Compose v5.7.1), approved by the project decision-maker on 2026-11-18 (plan "yes", start `dockerd`, add `matriz-net`, keep `postgres:16-bookworm`). All accounts and data are fictitious; no password is recorded here. `.env` was created from `.env.example` with freshly generated values and is ignored by Git.

### Services overview

| Service | Image/Build | Port | Healthcheck | Volume |
|---|---|---|---|---|
| `db` | `postgres:16-bookworm` (official, major version pinned) | none published (5432 inside `matriz-net` only) | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB`, every 5 s | `matriz-db` at `/var/lib/postgresql/data` |
| `app` | `build: .` (`Dockerfile`), image `matriz:local`; API and user interface in one process (AR-06) | `127.0.0.1:${MATRIZ_PORT:-8080}:8080` | `curl -fsS http://localhost:8080/config.js`, every 10 s, 30 s start period; waits for `db` healthy | none |
| `seed` | same image, profile `seed`, `SPRING_PROFILES_ACTIVE=seed` | none | none (one-shot, `restart: "no"`); waits for `db` healthy | none |

All three services use the dedicated network `matriz-net` (added in this task) and reach each other by service name (`db`). There is no separate frontend or proxy service because the architecture has none. No application change was needed: `GET /config.js` is open and serves as the readiness probe.

### Commands executed and results

| # | Command | Result |
|---|---|---|
| 1 | `docker compose config -q` | Exit 0 (valid). |
| 2 | `docker compose build` (repository `Dockerfile`) | Failed. First, Docker Hub answered `429 Too Many Requests` on pulling the base images; retrying the three `docker pull` commands worked after a few minutes. Then Maven failed in the build stage: `PKIX path building failed`, because this sandbox's HTTPS proxy re-signs certificates and the Maven image does not trust its CA. This is a limit of the sandbox, not of `compose.yaml`. |
| 3 | `docker compose -f compose.yaml -f <scratch override> build` | Succeeded, "Image matriz:local Built". The override (kept outside the repository, not committed) used a copy of the `Dockerfile` that only adds the sandbox truststore to Maven's options in the build stage. The runtime stage is identical to the repository's. |
| 4 | `docker compose up -d --wait` | `db` Healthy, then `app` Healthy. Flyway applied 4 migrations (v4) on the empty database; "Started MatrizApplication in 8.734 seconds". |
| 5 | `docker compose ps` | `app` Up (healthy) `127.0.0.1:8080->8080/tcp`; `db` Up (healthy) `5432/tcp` (not published). |
| 6 | `docker compose run --rm seed`, run twice | First run: "Demo data: 37 new rows inserted". Second run: "Demo data: 0 new rows inserted". |
| 7 | `docker compose logs --tail=50`, then a search of the full logs for `ERROR`, `FATAL`, `exception`, `panic` | No critical entry. Only the PostgreSQL `initdb` notice about "trust" authentication for local connections (see Limitations). |
| 8 | `docker compose down` (no `-v`), then `docker compose up -d --wait` | Containers and network removed, volume `matriz_matriz-db` kept; stack Healthy again. |
| 9 | `docker compose run --rm seed` after the restart, and once more | "Demo data: 0 new rows inserted" both times. |

### Validation checks

| Check | Expected | Observed | Status |
|---|---|---|---|
| `compose.yaml` is valid | `config -q` exits 0 | Exit 0 | Pass |
| No credentials in `compose.yaml` | Values come from `.env` | Only `${...}` references; a search for password/secret/token found only a comment | Pass |
| `.env` not versioned | Ignored by Git | `git check-ignore .env` prints `.env`; `git status` shows only `compose.yaml` | Pass |
| Dedicated network and service names | `matriz-net`, host `db` | Network lists both containers; `getent hosts db` in `app` returns 172.18.0.2; the application connected through `jdbc:postgresql://db:5432/matriz` | Pass |
| Only needed ports on the host | App only, on loopback | App on `127.0.0.1:8080`; connection to host port 5432 refused | Pass |
| Healthchecks and start order | `db` healthy before `app`; all healthy | `up --wait` ended with both Healthy | Pass |
| Non-root runtime | Not root | `id` in `app`: uid 10001 (`matriz`) | Pass |
| Application reachable | Page and config served | `GET /` 200 `text/html`; `GET /config.js` returns the client configuration | Pass (curl) |
| Frontend → API → database | Sign-in and data work | See the next four rows | Pass (API) |
| Sign in with a demo account | Session started | `ana@example.com`: CSRF 204, sign-in 200, `/auth/me` shows her memberships | Pass |
| Create a project | 201 | Project "Compose check project" created in "Acme Studio" | Pass |
| Create a task | 201 | "Compose check task" created | Pass |
| Assign a user | 201 | Assigning "Ben Demo" returned 422 ("Only active members of this project can be assigned", a documented rule: a new project has only its creator). Assigning "Ana Demo", the only eligible member, returned 201. | Pass (with the rule noted) |
| Add a comment | 201 | Comment created and listed | Pass |
| Persistence after `down` / `up` | Data remains | The project, assignee and comment were read back after the restart | Pass |
| Seed does not duplicate | Counts unchanged | Table counts before and after a further seed are identical: account 7, workspace 3, member 9, project 4, project_member 8, task 6, task_assignee 1, comment 4 | Pass |
| No unresolved critical errors in logs | None | None found | Pass |
| Use in a real browser | Page renders and works | Not verified: no browser run in this task; the checks above used curl | Not verified |

### Problems found and fixes

| Problem | Fix |
|---|---|
| No Docker daemon in the container | Started `dockerd` manually (approved). |
| Docker Hub unauthenticated pull limit (429) | Pulled the three base images one by one, repeating until each succeeded. |
| Maven could not verify the sandbox proxy's certificate during the image build | Used a scratch override with the sandbox truststore, outside the repository. The repository's `Dockerfile` was not changed. Not needed on a normal machine or in CI. |
| Compose used only the implicit default network | Added the named network `matriz-net` to all three services and a comment on the pinned image (`compose.yaml`). |
| My first helper script wrote two small ID files to `/` of the container (`/pid`, `/ws`) | `/pid` was moved away; `/ws` is still there. Removing it was blocked by a safety check and was not done. It is outside the repository and harmless. |

### Limitations and pending items

- The image was built with the scratch override (row 3), so the repository `Dockerfile` itself was not built successfully in this environment; CI (`.github/workflows/ci.yml`) is its normal build path.
- The user interface was not driven in a browser, and the Playwright test was not run.
- The `app` and `seed` services share the tag `matriz:local`; `docker compose build` builds it twice from the same cache.
- The PostgreSQL image's first start logs the standard `initdb` notice about "trust" authentication for local connections. The database is not published, and the role password comes from `.env`.
- Tag `postgres:16-bookworm` follows minor releases (decision: no minor pin). Hosting, backup and availability remain pending (TD-02, TD-05, TD-06).

### Proposed documentation updates for Exercise 05 (not applied)

- **README.md, "Run with Docker":** replace the last bullet, "Not verified in this repository's container: building the image and running Compose need a Docker daemon", with a pointer to this section. Add that the stack uses the network `matriz-net`, that assigning a user needs that user to be an active project member, and that Docker Hub's pull limit or a TLS-intercepting proxy can make the first build fail.
- **docs/architecture.md, "Technology stack":** mention the explicit network `matriz-net` in the Docker Compose row.
- **CHANGELOG.md:** add a line for the `matriz-net` network.
