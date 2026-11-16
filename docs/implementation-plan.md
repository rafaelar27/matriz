# Matriz — Implementation Plan

> **Status:** draft, created on 2026-11-12. It holds the increments planned so far; later increments are added when they are planned. Requirement IDs are defined in [requirements.md](requirements.md), user stories in [user-stories.md](user-stories.md), the stack in [architecture.md](architecture.md#technology-stack) and the data model in [database.md](database.md).

## Increments

| ID | Increment | Status |
|---|---|---|
| INC-01 | Project setup | Done (2026-11-12) |
| INC-02 | Authentication and access control | Done (2026-11-12) |
| INC-03 | Projects (first end-to-end flow) | Done (2026-11-12) |
| INC-04 | Tasks within a project | Done (2026-11-12) |
| INC-05 | Task collaboration: assignees (Feature A) and comments (Feature B) | Done (2026-11-12) |
| INC-06 | User interface, one screen at a time | In progress |
| INC-07 | Project owner required and date order | Done (2026-11-16) |

### INC-01: Project setup

**Goal:** a project skeleton that starts, connects to PostgreSQL, creates the schema reproducibly and loads demo data, with no feature logic (no sign-in, endpoints or UI).

**Basis:** [Technology stack](architecture.md#technology-stack), decision rules 1–4 and CMP-01 to CMP-13 in [architecture.md](architecture.md); all entities and BR-01 to BR-20 in [database.md](database.md); NFR-01, NFR-03; SP-01, SP-06; AGENTS.md C2.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC01.1 | The project has one package per module (CMP-01 to CMP-13) and uses only the documented stack. | Review of `pom.xml` and `src/main/java/com/matriz` | Pass |
| AC-INC01.2 | Configuration is read from environment variables; `.env.example` holds fictitious values only, and `.env` is ignored by Git. | `git check-ignore -v .env .env.example`; review of `.env.example` | Pass |
| AC-INC01.3 | Migrations create all 11 entities of database.md on an empty database, with no manual steps. | `mvn spring-boot:run` on an empty database: `Successfully applied 1 migration`; `DatabaseSetupTest` | Pass |
| AC-INC01.4 | The schema enforces the keys, uniqueness, required fields, enum values and delete behaviour of database.md. | SQL inserts, updates and deletes inside rolled-back transactions | Pass |
| AC-INC01.5 | The seed command loads demo data with at least two users and projects for different access levels; running it again inserts nothing and deletes nothing. | Seed command run twice; row counts compared | Pass |
| AC-INC01.6 | Demo passwords are stored as BCrypt hashes, never in plain text. | Query of `account.password_hash` prefix and length | Pass |
| AC-INC01.7 | Restarting the application keeps existing data. | Row counts before and after a restart | Pass |
| AC-INC01.8 | A database access check succeeds. | `mvn verify` (`DatabaseSetupTest`, 3 tests) | Pass |

**Deviations from the documentation, approved by the project decision-maker on 2026-11-12:**

- `account.password_hash` is added for demo passwords (BCrypt), although DA-08 says no credential data is modelled until TD-03 is decided. Updating database.md and TD-03 is proposed separately.
- Representation choices that the documentation leaves open: UUID identifiers (API-A4 leaves the format open), text columns without maximum lengths (PD-04), enums stored as upper-case codes with CHECK constraints.

**Not enforced by the schema** (left to the feature modules): BR-04, BR-09, BR-13 and BR-18, because they span several rows.

### INC-02: Authentication and access control

**Goal:** sign-in, the signed-in profile and sign-out with a server-side session; identity taken only from the session; every operation in [openapi.yaml](../openapi.yaml) authorized in the backend through one policy (CMP-03).

**Basis:** FR-01, FR-04, FR-05, FR-07, FR-09, FR-10, FR-12; US-01, US-10, US-11, US-19; AC-10.1 to AC-10.3, AC-11.1 to AC-11.3, AC-19.2; BR-02, BR-08, BR-09, BR-12; NFR-01; SP-01 to SP-04; TD-03 (decided 2026-11-12: session cookie, BCrypt, 8-hour idle timeout); API-A2, API-A3.

**Scope decided by the project decision-maker on 2026-11-12:** authentication plus the two operations already in openapi.yaml (`createTask`, `updateTaskStatus`). Endpoints for projects, comments, task lists, task editing and deletion are built in their own feature increments, with the rules recorded in the access rules table below.

**Access rules:**

| Operation | Who is allowed | Condition checked | If denied | FR/US |
|---|---|---|---|---|
| `GET /auth/csrf`, `POST /auth/sign-in` | Anyone | Sign-in: email and password match the BCrypt hash; CSRF header present | 401 `invalid_credentials` (same for every reason) / 403 `csrf_invalid` | FR-01, SP-01 |
| `GET /auth/me`, `POST /auth/sign-out` | Signed in | Valid session (and CSRF header for sign-out) | 401 / 403 `csrf_invalid` | FR-01 |
| `POST /projects/{id}/tasks` | Project Manager, Team Member | Active member of the project's workspace; not Viewer; **provisional:** not Workspace Administrator (PD-03), caller is a project member (PD-20), a Team Member sets only themselves as owner (PD-01); owner is a member of the project; creator taken from the session | 404 other workspace or unknown / 403 / 422 | FR-09, FR-10, BR-09, BR-12, AC-10.1–10.3, AC-19.2 |
| `PATCH /tasks/{id}` | The task's owner, if Team Member or Project Manager | Active member of the task's workspace; caller is the owner (**provisional** for Project Managers, PD-01); Viewers refused even as owner (**provisional**, PD-17) | 404 / 403 / 422 | FR-12, AC-11.1–11.3 |
| List or view projects, task list *(later increment)* | Every active member, Viewers included | Same workspace | 404 | FR-08, FR-13, AC-19.1 |
| Create, edit, archive projects; manage project members *(later)* | Project Manager | Same workspace; which Project Managers may edit is not documented; archiving pending PD-05 | 403 / 404 | FR-06, FR-07, US-06, US-07, US-08, US-24 |
| Edit a task, change its owner *(later)* | Project Manager (AC-10.4); others pending PD-01 | New owner is a project member | 403 / 422 | FR-09, FR-10, AC-10.4 |
| Delete a task *(later)* | Its creator only | `created_by` is the caller; Viewers refused | 403 | FR-09, BR-12, AC-26.1, AC-26.2 |
| Add or list comments *(later)* | Add: Team Member, Project Manager; list: every member | Author taken from the session; whether the commenter must be a project member is not documented | 403 / 404 | FR-16, AC-19.2 |

**403 or 404:** a resource in a workspace the caller does not belong to returns 404 (API-A3); a refused action inside the caller's workspace returns 403.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC02.1 | Valid credentials sign in and return the profile, which never contains the password or hash. | `AuthenticationTest`; curl against the running app | Pass |
| AC-INC02.2 | Invalid credentials are refused with the same generic 401 for a wrong password, an unknown email and empty fields. | `AuthenticationTest`; curl | Pass |
| AC-INC02.3 | Requests without authentication get 401. | `AuthenticationTest`; curl | Pass |
| AC-INC02.4 | After sign-out or idle expiry, the old session cookie gets 401 `session_expired`; sign-in issues a new session id. | `AuthenticationTest`; curl with the old cookie after sign-out; curl after 75 s with `MATRIZ_SESSION_TIMEOUT=1m` | Pass |
| AC-INC02.5 | A member of one workspace cannot create tasks in, or change tasks of, another workspace (404). | `TaskAccessTest`; curl (Ben on "Lab inventory") | Pass |
| AC-INC02.6 | Client-supplied identity (`created_by_member_id`) or status is refused; the creator is always the signed-in member. | `TaskAccessTest`; curl | Pass |
| AC-INC02.7 | An owner who is not a member of the project, or is in another workspace, is refused (422). | `TaskAccessTest` | Pass |
| AC-INC02.8 | Role rules and provisional rules apply (Viewer, Workspace Administrator, Team Member, non-owner, non-project-member, deactivated member). | `TaskAccessTest` | Pass |
| AC-INC02.9 | Requests that change data without a CSRF token are refused (403). | `AuthenticationTest`; curl | Pass |
| AC-INC02.10 | No password or hash appears in logs. | Search of application and test logs | Pass |

**Not implemented in INC-02:** the "task assigned" notification side effect of `createTask` (FR-17, CMP-08), left for the notifications increment.

**Requirements for the UI increment** (no UI exists yet):

- Every screen except sign-in requires a session; on 401 `not_authenticated` the UI goes to the sign-in screen.
- On 401 `session_expired` the UI shows a clear message, "Your session has ended. Sign in again.", and goes to the sign-in screen.
- The UI calls `GET /auth/csrf` before signing in and sends the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header on every request that changes data.
- Actions refused by Access Control are hidden (CMP-11), but the backend checks remain the rule.

### INC-03: Projects (first end-to-end flow)

**Goal:** a user signs in, creates a project, sees it in the project list, and can still view it after reloading the page and restarting the backend. Includes listing with search and pages, viewing, editing and deleting projects, and the first user interface (CMP-11).

**Basis:** FR-05, FR-06, FR-07, FR-08, FR-18; US-06, US-09, US-17, US-19, US-24, US-27, US-28; AC-17.1, AC-19.1, AC-19.3, AC-24.1, AC-27.1 to AC-27.3, AC-28.1 to AC-28.3; EC-05; BR-08, BR-15, BR-18, BR-21; NFR-01, NFR-06; API-A3. Decisions of 2026-11-12 in the [decision log](constraints.md#decision-log). Provisional: Workspace Administrators cannot manage projects (PD-03). Still pending: archiving and project statuses (PD-05).

**Built:** operations `listProjects`, `createProject`, `getProject`, `updateProject`, `deleteProject`, `getProjectProgress` and `listWorkspaceMembers` in [openapi.yaml](../openapi.yaml) (version 0.3.0-draft); migration `V2__project_delete_cascade.sql`; the demo user Dan Demo (Northwind Lab only); screens for sign-in, project list, project detail, create and edit form, and delete confirmation.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC03.1 | A Project Manager creates a project; it appears in the list and its detail can be opened. | `ProjectApiTest`; browser (Ana created "Spring catalogue") | Pass |
| AC-INC03.2 | The project is still listed and viewable after a page reload and a backend restart. | Browser reload; app restarted, row checked with SQL, project opened again after signing in | Pass |
| AC-INC03.3 | Name 3 to 100 characters and description at most 1000 are enforced, with a message at the field. | `ProjectApiTest` (2, 3, 100, 101, 1000, 1001); browser (2 and 101 characters) | Pass |
| AC-INC03.4 | Invalid dates, unknown fields and an owner from another workspace are refused (422). | `ProjectApiTest` | Pass |
| AC-INC03.5 | Only Project Managers create, edit and delete projects; Team Members, Viewers and Workspace Administrators get 403. | `ProjectApiTest` | Pass |
| AC-INC03.6 | A user of another workspace cannot list, view, edit or delete a project, or see its progress or members (404). | `ProjectApiTest` (Dan) | Pass |
| AC-INC03.7 | Editing a project changes what the list shows (AC-24.1). | `ProjectApiTest`; browser (rename) | Pass |
| AC-INC03.8 | Deleting asks for confirmation and removes the project, its tasks, comments, notifications and project members. | `ProjectApiTest` (orphan checks); browser (Cancel, then Delete) and SQL check | Pass |
| AC-INC03.9 | Search matches part of the name ignoring case; results are paged. | `ProjectApiTest`; browser (search; pages not exercised, only 3 projects) | Pass |
| AC-INC03.10 | Progress shows "done of total" per project, "0 of 0" without tasks. | `ProjectApiTest`; browser | Pass |
| AC-INC03.11 | Without a session every operation gets 401, and the UI goes to sign-in with a message after the session ends. | `ProjectApiTest`; browser after a backend restart | Pass |

**Not implemented in INC-03:** archiving and project statuses (PD-05), choosing project members (US-08), the task list and task screens.

### INC-04: Tasks within a project

**Goal:** create, list, view, edit and delete the tasks of an accessible project, change their status, filter by status and owner with pages, and keep lists and counts up to date without reloading the page.

**Basis:** FR-05, FR-09 to FR-13, FR-15, FR-18; US-10, US-11, US-12, US-14, US-19, US-25, US-26; AC-10.1 to AC-10.5, AC-11.1 to AC-11.3, AC-17.2, AC-17.3, AC-19.1, AC-19.2, AC-25.1, AC-25.2, AC-26.1 to AC-26.3; BR-09 to BR-12, BR-15; DA-04, DA-05; NFR-01; API-A3. Decisions of 2026-11-12 in the [decision log](constraints.md#decision-log): title and description lengths (PD-21; PD-04 in part), Portuguese status labels (PD-13 in part), optional status on create, filters and order, provisional task permissions. **Left out:** task priority (F17, outside the initial scope) and filtering by due date.

**Built:** [openapi.yaml](../openapi.yaml) 0.4.0-draft with `listTasks`, `getTask`, `updateTask` and `deleteTask` and the changed `createTask`; `TaskService`, `TaskController`, `TaskFields`, `TaskView`, `TaskRepository` queries and `AccessPolicy.checkEditTask` and `checkDeleteTask`; the Tasks section of the project view (filters, pages, counts per status, create and edit form, status list per row, read-only details, delete confirmation). No migration was needed.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC04.1 | A task created without a status is `To do`; the list, counts and progress update at once. | `TaskApiTest`; browser ("Collect logo files", response `"status":"To do"`, Por fazer 1 → 2) | Pass |
| AC-INC04.2 | Title: 2 and 151 characters, spaces only and missing are refused; 3 and 150 are accepted; limits apply after trimming. | `TaskApiTest`; browser (spaces only) | Pass |
| AC-INC04.3 | Description: 2000 characters accepted, 2001 refused, on create and edit. | `TaskApiTest` | Pass |
| AC-INC04.4 | A status other than `To do`, `In progress`, `Done` (such as `DONE`, `done`, `Concluída`) is refused on create, edit and as a filter. | `TaskApiTest` | Pass |
| AC-INC04.5 | Filters by status, by owner and combined, with pages over the filtered result and the documented order. | `TaskApiTest`; browser (Em curso + Ben) | Pass |
| AC-INC04.6 | Status changes and edits persist after a page reload and a backend restart. | Browser reload; app restarted, rows checked with SQL, list shown again after signing in | Pass |
| AC-INC04.7 | Lists and counts update without reloading after create, edit, status change and delete. | Browser | Pass |
| AC-INC04.8 | A user of another workspace cannot list, view, create, edit or delete the project's tasks (404). | `TaskApiTest` (Dan) | Pass |
| AC-INC04.9 | A task reached through a different project id is not found (404), and is not changed. | `TaskApiTest` | Pass |
| AC-INC04.10 | Deleting a task asks for confirmation and leaves no comments or notifications of it. | `TaskApiTest` (orphan checks); browser (Cancel, then Delete) and SQL check | Pass |
| AC-INC04.11 | The interface shows Portuguese status labels; requests and responses use the API values. | Browser (option values and network bodies) | Pass |
| AC-INC04.12 | Permissions: Viewers and Workspace Administrators read only; Team Members edit their own tasks and never reassign; only owners change status; only creators delete; a refused request changes nothing. | `TaskApiTest`, `TaskAccessTest` | Pass |
| AC-INC04.13 | Without a session every task operation gets 401, and the interface goes to sign-in. | `TaskApiTest`; browser after a restart | Pass |

**Known gap:** AC-10.3 ("Ben is not offered as an owner") is not met in the interface: the form offers every workspace member, because no endpoint lists a project's members (decided 2026-11-12). The backend refuses owners outside the project (422).

### INC-05: Task collaboration

**Goal:** assign several eligible members to a task and remove them one by one (Feature A); add and list comments with author and date (Feature B). Built and reviewed one feature at a time.

**Basis:** FR-05, FR-07, FR-10, FR-16, FR-22; US-15, US-29; AC-10.3, AC-15.1 to AC-15.4, AC-29.1 to AC-29.5; EC-04, EC-08, EC-10; BR-13, BR-22; DA-05, DA-07; NFR-01; API-A3. Decisions of 2026-11-12 in the [decision log](constraints.md#decision-log): assignees besides the single owner (FR-10 unchanged), provisional assignment and comment permissions, comment rule (1 to 2000 characters, trimmed), oldest-first order. **Left out:** the "comment added" and "task assigned" notifications (FR-17, CMP-08), editing or deleting comments (not in the MVP), and task priority (F17).

**Built:** [openapi.yaml](../openapi.yaml) 0.6.0-draft with `listEligibleAssignees`, `listTaskAssignees`, `assignTask`, `unassignTask`, `listTaskComments` and `addTaskComment`; migration `V3__task_assignees.sql` (primary key on task and member); `AssignmentService` and `AssignmentController` in CMP-05; the `comments` module (CMP-07) using the public `TaskDirectory`; `AccessPolicy.checkAssign` and `checkComment`; Assignees and Comments areas in the task details.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC05.1 | Only active project members are listed as eligible and can be assigned, also through direct requests; no email or account data is returned. | `AssignmentApiTest`; browser (Carla not offered; direct request 422) | Pass |
| AC-INC05.2 | A non-existent or non-eligible member is refused (422) and nothing is saved. | `AssignmentApiTest` | Pass |
| AC-INC05.3 | Assigning the same member twice is refused with 409; the database refuses a duplicate row. | `AssignmentApiTest` (backend and direct insert); browser (direct request 409) | Pass |
| AC-INC05.4 | Several assignees work; removing one keeps the others; the owner is unchanged. | `AssignmentApiTest`; browser | Pass |
| AC-INC05.5 | Only Project Managers assign or remove (403 otherwise); other workspaces and mismatched project ids get 404. | `AssignmentApiTest` | Pass |
| AC-INC05.6 | Assignments persist after reopening the task, a reload and a backend restart. | Browser; SQL after restart | Pass |
| AC-INC05.7 | Comment content: 0 characters, only spaces, null and missing refused; 1 and 2000 accepted; 2001 refused; limits apply after trimming. | `CommentApiTest`; browser (spaces only); direct requests | Pass |
| AC-INC05.8 | Author and date are set by the server; a sent author or date is refused (422). | `CommentApiTest`; direct requests from the browser | Pass |
| AC-INC05.9 | Comments are listed oldest first with author name and date, without account data. | `CommentApiTest`; browser | Pass |
| AC-INC05.10 | Viewers, Workspace Administrators and non-members of the project cannot comment but can read; other workspaces and mismatched ids get 404. | `CommentApiTest` | Pass |
| AC-INC05.11 | Comments persist after reopening the task, a reload and a backend restart. | Browser; SQL after restart | Pass |
| AC-INC05.12 | Assignees and comments update without reloading the page; every operation without a session gets 401. | Browser; `AssignmentApiTest`, `CommentApiTest` | Pass |

### INC-06: User interface

**Goal:** rework the user interface one screen at a time, with shared form parts that every later form uses. Screen 1: sign-in. Screen 2: dashboard. Screen 3: the project list (part 3a) and the create and edit project form on the shared form parts (part 3b).

**Basis:** FR-01; US-01; SP-01; TD-03 (decided 2026-11-12); NFR-06, NFR-07; CMP-11; UI-A1, UI-A7. Decisions of 2026-11-12 in the [decision log](constraints.md#decision-log): the `MATRIZ_API_BASE_URL` setting served as `GET /config.js` (open without a session, same-site paths only), the email kept and the password cleared after a failed sign-in, UI-A7. Screen 2: FR-08, FR-13, FR-18; US-09, US-17, US-19; AC-17.1, AC-19.1, AC-19.3; EC-05; NFR-01; E-01 to E-20, UI-A1, UI-A2, UI-A6 in [docs/ui/README.md](ui/README.md); PD-03 and PD-16 still pending; F41 stays outside the initial scope. Dashboard decisions Q1 to Q7 of 2026-11-12 in the [decision log](constraints.md#decision-log). Screen 3a: FR-08, FR-13, FR-18; US-09, US-19, US-28; AC-19.1, AC-28.1 to AC-28.3; NFR-01; E-03, E-13, UI-A6 in [docs/ui/README.md](ui/README.md); F41 stays outside the initial scope. Screen 3 decisions of 2026-11-12 in the [decision log](constraints.md#decision-log): screen 3 covers the project list and the project form; the Projects screen keeps "Loading projects…" and its error state (UI-A4 not applied there); task counts by status per row; a page past the last opens the last page, keeping the search; 20 projects per page; API tests as evidence for the role-specific checks. Screen 3b: FR-06; US-06, US-24, AC-24.1; NFR-06; E-14 in [docs/ui/README.md](ui/README.md); BR-21 and BR-23 in [database.md](database.md); PD-10 (decided in part). Decisions in the [decision log](constraints.md#decision-log): of 2026-11-12, a project always has an owner, with no "No owner" choice, and an owner who is no longer an active member shows as "Former member"; of 2026-11-16, QC1 to QC3, review findings R-01, R-03 and R-04 of part 3b, and API tests as evidence for the role-specific checks of AC-INC06.42 and the API side of AC-INC06.43.

**Built (screen 1, sign-in):** `ClientConfigController` (`GET /config.js`) and the property `matriz.api-base-url`; `SecurityConfig` opens `GET /config.js`; in `static/js/app.js`, one place for the API base URL, the shared form parts `formKit`, `guarded` and `errorText`, the reworked sign-in screen and the return to the earlier screen (UI-A7); the network message "Matriz could not be reached. Check your connection and try again."; tests `ClientConfigApiTest` and `ClientConfigControllerTest`. No migration; no change to the operations in [openapi.yaml](../openapi.yaml).

**Built (screen 2, dashboard):** in `static/js/app.js`, `dashboardScreen` on the route `#/dashboard`, which is `HOME`, the first screen (UI-A1), also for unknown addresses; the top bar with E-01 (the names of every workspace of the member, text only, PD-16) and the main menu Dashboard and Projects, the current item marked with `aria-current`; shared helpers `section()` (E-19 loading and E-20 error with Try again, one load at a time), `loadProjectsWithProgress()`, `projectGrid()`, `statusCountTags()`, `newProjectLink()` and `projectCount()`, used by the dashboard, the Projects screen and the task list; `failure()` uses `errorText()`; parallel 401 responses go to sign-in once and keep "Your session has ended. Sign in again."; a project whose task counts cannot be loaded shows "—" in its row. In `index.html`, the Dashboard menu item and the E-01 text; in `app.css`, a wrapping menu and dashboard section styles. `DemoDataSeeder` adds the workspace Orchard Office, without projects, with Eva Demo (Project Manager) and Finn Demo (Workspace Administrator) (Q6). No migration, no change to [openapi.yaml](../openapi.yaml), no access-rule change, no new automated tests.

**Built (screen 3, part 3a, Projects list):** in `static/js/app.js`, `projectsScreen` and `projectTable` on the route `#/projects`: every project of the member's workspaces, 20 per page (`PROJECT_LIST_PAGE_SIZE`, the `listProjects` default; the dashboard keeps 10), each row with the project's task counts by status as on the dashboard; search by part of the name, trimmed, with the field capped at 100 characters; a page past the last opens the last page that has projects, keeping the search, and replaces the address so Back skips the empty page; "Loading projects…" and the screen's own error state with Try again; an answer that arrives after the member has moved on is ignored. Seven tests added to `ProjectApiTest`. No migration, no change to [openapi.yaml](../openapi.yaml) (0.6.0-draft), no access-rule change.

**Built (screen 3, part 3b, project form):** in `static/js/app.js`, `projectFormScreen` on `formKit('project')` and `guarded`, replacing its own form code. Workspace: on create, only workspaces where the member is a Project Manager; fixed on edit, with the hint "A project stays in its workspace.", and `workspace_id` is never sent on edit. Owner: active members only, with no "No owner" choice; the creator by default; an owner who is no longer an active member shows as "Former member", without a name, and is kept unless someone else is chosen; only the id of a member from the loaded list is sent, never "Loading members…", "Members could not be loaded" or "Former member"; an answer to an earlier member-list request is ignored. The Owner hint "You are the owner unless you choose someone else." is shown on create only (QC3). Dates: a start date after the due date is refused at Due date before sending; an incomplete date or a year that is not 4 digits gets "Enter a valid date." (QC2, review finding R-04 of part 3b). Edit sends only the changed fields; with no change it sends nothing and opens the project with "Changes saved." (QC1). Field errors from a 422 appear at their fields, other failures in the banner, and the values are kept. Four tests added to `ProjectApiTest` (128 in total). No migration, no change to [openapi.yaml](../openapi.yaml) (0.7.0-draft), no server code or access-rule change.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC06.1 | `GET /config.js` is open without a session, returns the base URL (empty by default), is not cached and holds no secret; every other operation still needs a session. | `ClientConfigApiTest` | Pass |
| AC-INC06.2 | `MATRIZ_API_BASE_URL` accepts only an empty value or a same-site path; any other value stops startup, and an accepted value is safely encoded. | `ClientConfigControllerTest` | Pass |
| AC-INC06.3 | After the session ends, the interface goes to sign-in with "Your session has ended. Sign in again." | Browser (expired session) | Pass |
| AC-INC06.4 | Empty fields and an email without "@" show a message at the field and focus the first one. | Browser | Pass |
| AC-INC06.5 | A wrong password shows "The email or password is incorrect.", keeps the email and clears the password; repeated clicks send one request. | Browser (fictitious wrong password, 3 rapid clicks, 1 request) | Pass |
| AC-INC06.6 | Signing in again with the same account returns to the screen the member was on (UI-A7). | Browser (earlier project page) | Pass |
| AC-INC06.7 | A signed-in member who opens sign-in goes to the first screen, the dashboard (UI-A1, UI-A7). | Browser (`#/sign-in` as Ana, re-checked with the dashboard) | Pass |
| AC-INC06.8 | Signing out shows "You have signed out." | Browser | Pass |
| AC-INC06.9 | The sign-in screen has no horizontal scroll at 375 and 768 px wide. | Browser | Pass |
| AC-INC06.10 | After signing in, and when a signed-in member opens sign-in, the dashboard opens with Dashboard marked as current in the main menu (UI-A1). | Browser as Ana | Pass |
| AC-INC06.11 | E-01 shows the names of every workspace the member belongs to, as text, without a switcher (PD-16). | Browser as Ana ("Acme Studio · Northwind Lab") and Eva ("Orchard Office") | Pass |
| AC-INC06.12 | The Projects section shows at most 10 projects, the total from the API, "Showing N of total" and "See all projects". | Browser as Ana: 3 projects, "Showing 3 of 3"; with 11 fictitious projects added, "14 projects", 10 rows, "Showing 10 of 14"; test projects deleted afterwards and SQL showed no test rows and no orphans | Pass |
| AC-INC06.13 | Each row shows workspace, owner, due date, progress "N of M tasks done" and the project's task counts by status with Portuguese labels, equal to `listTasks` `status_counts`; no total across projects (UI-A6, F41). | Browser as Ana: "3 of 12 tasks done", Por fazer 5 / Em curso 4 / Concluída 3, compared with the API response | Pass |
| AC-INC06.14 | A workspace without projects shows "No projects yet."; a Project Manager also sees "+ New project", a Workspace Administrator does not (E-15, PD-03 provisional). | Browser as Eva and Finn | Pass |
| AC-INC06.15 | On an empty project list "+ New project" appears once, in the empty state, on the dashboard and the Projects screen (Q7). | Browser as Eva after the fix: once on the dashboard and once on the Projects screen | Pass |
| AC-INC06.16 | A section that cannot be loaded shows "This section could not be loaded." and Try again, which loads it again once (E-20). | Browser (recovery checked; the single-load guard added afterwards not re-checked) | Pass (recovery); single load Not verified |
| AC-INC06.17 | A project whose task counts cannot be loaded shows "—" in its row; the rest of the section loads. | Browser | Not verified |
| AC-INC06.18 | A member of another workspace cannot reach a project, its tasks or its progress through direct requests (NFR-01). | Browser as Eva: Acme project and its tasks 404, progress empty | Pass |
| AC-INC06.19 | The dashboard has no page horizontal scroll at 375 and 768 px; the menu, account menu, "+ New project" and "See all projects" stay visible. | Browser | Pass |
| AC-INC06.20 | No migration, contract or access change; all automated tests pass. | `mvn verify` (104 tests); security audit (pass, 2 low findings) | Pass |
| AC-INC06.21 | Projects (E-03) and See all projects (E-13) open `#/projects`, with Projects marked as current. | Browser as Ana | Pass |
| AC-INC06.22 | Each row shows workspace, owner, due date, progress and the project's task counts by status; a project without tasks shows "0 of 0 tasks done"; a project whose counts cannot be loaded shows "—" (D-Q3). | Browser as Ana, compared with `progress` and `listTasks` `status_counts` for all 3 projects | Pass; "—" case Not verified |
| AC-INC06.23 | A Viewer sees the same project list as a Project Manager (AC-19.1). | `ProjectApiTest.viewerListsTheSameAcmeProjectsAsAProjectManager` (API evidence accepted 2026-11-12) | Pass |
| AC-INC06.24 | A Team Member sees projects they are not a member of (FR-08). | `ProjectApiTest.teamMemberListsAProjectTheyAreNotAMemberOf`, added 2026-11-16 (API evidence accepted 2026-11-12) | Pass |
| AC-INC06.25 | "+ New project" is shown to Project Managers only, and once in the empty state. | Browser as Ana; other roles: `ProjectApiTest.onlyProjectManagersCreateEditAndDelete` (API evidence accepted 2026-11-12) | Pass |
| AC-INC06.26 | Search: "WEB" lists "Website redesign" only; "zzz" shows the no-match message; spaces only list every project; 100 characters are accepted and 101 refused (422, field `q`); the field stops a paste at 100 characters (US-28, AC-28.1, AC-28.2). | `ProjectApiTest` (`searchTextLengthBoundaries`, `whitespaceOnlySearchListsEverything`, `upperCaseSearchMatchesWebsiteOnly`); browser | Pass |
| AC-INC06.27 | 20 projects per page with Previous and Next; each project appears on exactly one page; the search is kept (D-Q5, AC-28.3). | Browser with 21 fictitious projects (24 in total: 20 and 4, no overlap, `q` kept), deleted afterwards (all 204; the API lists none) | Pass; SQL orphan check Not verified |
| AC-INC06.28 | A page past the last opens the last page that has projects, keeping the search; Back never returns to the empty page (D-Q4). | Browser (`?q=e&page=99`, `?page=5`, `?q=zz paging&page=7`); `ProjectApiTest.pagePastTheLastIsEmptyWithTheRealTotal` | Pass |
| AC-INC06.29 | Progress and counts update after a status change or the deletion of a To-do task; an edited due date is shown. | Browser | Pass |
| AC-INC06.30 | No project of another workspace is listed (NFR-01). | `ProjectApiTest.filteringByAWorkspaceTheCallerIsNotInIsEmpty`, `userOfAnotherWorkspaceCannotListViewEditOrDelete` (API evidence accepted 2026-11-12) | Pass |
| AC-INC06.31 | "Loading projects…" while loading; a failure shows "This section could not be loaded." and Try again, which loads the list again (D-Q2). | Browser (requests blocked, then restored) | Pass |
| AC-INC06.32 | Every field is labelled; hints and errors are tied to their fields (`aria-describedby`); the first error is focused; the banner says "Check the highlighted fields." | Browser as Ana | Pass |
| AC-INC06.33 | Create offers only the member's Project Manager workspaces; on edit the workspace is fixed, with the hint "A project stays in its workspace.", and `workspace_id` is never sent. | Browser as Ana, request bodies recorded (create offered Acme Studio only) | Pass |
| AC-INC06.34 | Name: 2 and 101 characters, spaces only and "  ab  " are refused at the field with no request; 3 and 100 are accepted after trimming; the same on edit. | Browser as Ana (2, 101, spaces only and "  ab  " refused with no request; a name with surrounding spaces saved trimmed); `ProjectApiTest` (`nameLengthBoundaries`, `editRulesMatchCreate`) | Pass; 3 and 100 characters and the edit case in the browser Not verified (covered by the API tests) |
| AC-INC06.35 | Description: 1000 characters accepted, 1001 refused; emptying it on edit sends `null`. | Browser as Ana; `ProjectApiTest.descriptionLengthBoundaries` | Pass |
| AC-INC06.36 | No "No owner" choice; the creator is the default; another active member can be chosen and saved (Ben; Vera, a Viewer); a placeholder is never sent; with the member list blocked, the project is created with the creator as owner; a late answer after a workspace change is ignored. | Browser as Ana (Ben chosen and saved; member list blocked: "Members could not be loaded", no owner sent, the creator became owner); `ProjectApiTest` (AC-INC07.1 and AC-INC07.3 tests) | Pass; Vera in the browser, and the late answer (Ana manages one workspace), Not verified |
| AC-INC06.37 | A start date after the due date is refused at Due date with no request; a server 422 appears at the same field; equal, single and cleared dates are accepted; an incomplete date or a year that is not 4 digits gets "Enter a valid date." (QC2, R-04). | Browser as Ana (start after due refused before sending; year 10000 refused; equal dates accepted; a cleared start sent as `null`; a server 422 at Due date after the stored start was moved in the background, values kept); `ProjectApiTest` (AC-INC07.4 and AC-INC07.5 tests) | Pass; an incomplete date, and `validity.badInput` outside Chromium, Not verified |
| AC-INC06.38 | Edit sends only the changed fields; AC-24.1 (2026-12-15 → 2026-12-22 shown in the list, then set back); no change sends nothing and opens the project with "Changes saved." (QC1). | Browser network panel as Ana (Website redesign) | Pass |
| AC-INC06.39 | An owner who was deactivated shows as "Former member" in the owner choice, with the name nowhere in the form; saving another field keeps that owner; choosing Ana changes it; the project list, detail and dashboard still show the name (PD-10, decided in part 2026-11-12). | Browser as Ana with Ben's Acme Studio membership deactivated and then restored (C1, approved; 0 inactive members afterwards); `ProjectApiTest.detailAndListStillShowADeactivatedOwnersName` | Pass |
| AC-INC06.40 | *Withdrawn 2026-11-16:* a project without an owner can no longer exist after migration V4 (INC-07). | — | Withdrawn |
| AC-INC06.41 | Values are kept after a 422, a 403 and a blocked network; server field errors appear at the field; one request per submission. | Browser as Ana (blocked network: "Matriz could not be reached…"; simulated 403; the server 422 of AC-INC06.37; three quick clicks sent one request) | Pass |
| AC-INC06.42 | Ben, Vera and Finn get the screen message; Dan gets "This project was not found."; their direct requests get 403 or 404; request bodies hold only documented fields. | Browser as Ana (a Northwind Lab project, where she is not a Project Manager: "Only Project Managers can edit projects."; an unknown id: "This project was not found."; request bodies recorded under AC-INC06.33 and AC-INC06.38); `ProjectApiTest` (`onlyProjectManagersCreateEditAndDelete`: Ben, Vera and Carla get 403; `userOfAnotherWorkspaceCannotListViewEditOrDelete`: Dan gets 404; `invalidDatesAndUnknownFieldsAreRefused`, `updateRefusesIdentityAndServerSetFields`) (API evidence accepted 2026-11-16) | Pass |
| AC-INC06.43 | A 401 leads to sign-in and back to the same address for the same account (UI-A7). | `ProjectApiTest.everyProjectOperationNeedsASession` (API side; API evidence accepted 2026-11-16) | Pass (API side); the return to the same address in the browser Not verified |
| AC-INC06.44 | Created and edited projects persist after a reload and a restart (NFR-03). | Browser; read-only comparison with a snapshot after a page reload and an application restart | Pass |
| AC-INC06.45 | Keyboard only, not colour alone, no horizontal scroll at 375 and 768 px (form and list); the dashboard still lists at most 10; no migration, contract or access change; all tests pass. | Browser (375 and 768 px, tab order); code review (errors are text); diff review (only `app.js` and `ProjectApiTest` changed); `mvn verify` with the application stopped (128 tests, 0 failures, BUILD SUCCESS) | Pass |
| AC-INC06.46 | `listWorkspaceMembers` leaves out a deactivated member; a `PATCH` without `owner_member_id` keeps a deactivated owner; re-sending that id gives 422. | `ProjectApiTest` (`workspaceMembersLeaveOutADeactivatedMember`, `patchWithoutOwnerKeepsADeactivatedOwner`, `resendingADeactivatedOwnerIsRefused`) | Pass |
| AC-INC06.47 | `projectFormScreen` uses `formKit` and `guarded` only; its own field and error code is gone; no other screen changed. | Diff review (reviewer, 2026-11-16) | Pass |

**Not verified yet:** another account signing in after a 401 goes to the first screen; the screen is forgotten after signing out; the network message when Matriz cannot be reached. Dashboard: the Viewer and Team Member dashboards in the browser (access covered by API tests); a screen reader; AC-INC06.17 and the single load of Try again after the fix; an unknown address going to the dashboard (code review only); parallel 401 responses going to sign-in once. Projects screen (3a): the SQL check that no temporary project remains after the paging check; the "—" row when a project's task counts cannot be loaded; the Viewer, Team Member and other-workspace checks in the browser (covered by API tests, accepted 2026-11-12). Project form (3b): the answer to an earlier member-list request being ignored after a workspace change (AC-INC06.36; Ana manages one workspace); Vera chosen as owner in the browser; 3 and 100 characters and the edit case of AC-INC06.34 in the browser; an incomplete date (AC-INC06.37); `validity.badInput` in browsers other than Chromium; the return to the same address after a 401 and signing in again, in the browser (AC-INC06.43, UI-A7).

**Not done yet:** the other screens (project detail, tasks, assignees, comments) are reworked in later steps of INC-06. The task form keeps its own form code until then. On the dashboard, E-04 to E-06, E-09 to E-11, E-16 to E-18 and the E-07 items Send feedback and Request my personal data are not built, because FR-14, FR-17, FR-03/FR-04, FR-20 and FR-21 have no API operations yet (Q2, decided 2026-11-12). Open low security findings: S-01, `listTasks` with `size=1` returns one unused task per project, and the Projects screen now sends up to 20 such calls per page (22 requests per page in total); S-02, no Content-Security-Policy header; part 3b audit S-01, `formKit` keeps its fields in a plain object, so a 422 field name such as `__proto__` could break the error display (shared code, left for later).

**Known gap:** a signed-in person with no active workspace sees an empty workspace name (E-01) and "No projects yet." on the dashboard. What they should see is pending PD-24; this stays as built until PD-24 is decided.

**Open questions (screen 3a, undecided):** review finding R-03 of screen 3a (US-28): the address can hold a search longer than 100 characters; whether Try again may send a second request on the Projects screen (follow-up to the decision that the screen keeps its own error state); AC-INC06.26: whether the 100-character search limit counts before or after trimming — the API trims first, while the field's maxlength counts untrimmed text.

### INC-07: Project owner required and date order

**Goal:** every project has an owner, and a project's start date is never after its due date, in the API, the service and the database. INC-06 screen 3 part 3b, the project form, waits for this increment.

**Basis:** FR-06; NFR-01; BR-21, BR-23 and R-06 in [database.md](database.md); DA-03 (owner part rejected 2026-11-12). Decisions of 2026-11-12 in the [decision log](constraints.md#decision-log): a project always has an owner, and an empty owner (`null`) is refused; a start date after the due date is refused with 422. Decisions of 2026-11-16: the database agent's check on scratch databases is the evidence for AC-INC07.8, and `mvn test` was accepted as the evidence for AC-INC07.10 until `mvn verify` ran to the end the same day.

**Built:** [openapi.yaml](../openapi.yaml) 0.7.0-draft: `createProject` and `updateProject` describe both rules; `ProjectCreateRequest.owner_member_id` and `ProjectUpdateRequest.owner_member_id` can no longer be `null`; `ProjectSummary.owner_member_id` is required; `ProjectValidationFailed` has the examples `ownerMissing` and `datesOutOfOrder`. In `ProjectService`, an owner sent as `null` is refused with 422 (`owner_member_id`, "An owner is required."), and a start date after the due date, checked on the project as it is after the change, is refused with 422 (`due_date`, "The due date cannot be before the start date."); a project created without `owner_member_id` still has the caller as owner. Migration `V4__project_owner_required_and_date_order.sql`: `project.owner_member_id` NOT NULL and the check `project_date_order_ck` (start_date IS NULL OR due_date IS NULL OR start_date <= due_date); it changes no rows and fails as a whole if a project breaks either rule. `ProjectApiTest`: `editRulesMatchCreate` changed (a `null` owner is now refused) and 13 tests added (124 in total). No access-rule change, no seed change, no user-interface change.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC07.1 | `createProject` without `owner_member_id` makes the caller the owner; with `owner_member_id: null`, 422 on `owner_member_id` ("An owner is required."), and nothing is saved. | `ProjectApiTest` (`createWithoutOwnerMakesTheCallerTheOwner`, `createWithNullOwnerIsRefused`, with SQL counts) | Pass |
| AC-INC07.2 | `updateProject` with `owner_member_id: null` gives 422 and the owner is unchanged; the updated `editRulesMatchCreate` passes. | `ProjectApiTest` (`updateWithNullOwnerChangesNothing`, `editRulesMatchCreate`); running build against `matriz` (2026-11-16): `PATCH /projects/d0000000-0000-0000-0000-000000000001` with a new name and a `null` owner gave 422 on `owner_member_id`, and the project read back unchanged | Pass |
| AC-INC07.3 | Another active member of the workspace (a Viewer too) is accepted as owner; a member of another workspace, a deactivated member or an id that is not valid is refused (422). | `ProjectApiTest` (`viewerCanBeChosenAsOwner`, `ownerThatIsNotAnActiveMemberOfTheWorkspaceIsRefused`, `deactivatedMemberIsRefusedAsOwner`, `ownerFromAnotherWorkspaceIsRefused`) | Pass |
| AC-INC07.4 | Start = due accepted; start one day after due refused on create (422, `due_date`); only one date, or neither, accepted. | `ProjectApiTest` (`createAcceptsDatesInOrderOrMissing`, `createWithStartAfterDueIsRefused`) | Pass |
| AC-INC07.5 | On edit, the rule applies to the resulting project: with start 2026-11-01 stored, sending only `due_date: 2026-10-31` is refused; sending only a `start_date` after the stored due date is refused; clearing one date is accepted; a refused request changes nothing. | `ProjectApiTest` (`editRefusesASingleDateThatBreaksTheOrder`, `editChecksTheResultingProject`); running build against `matriz` (2026-11-16): `PATCH /projects/d0000000-0000-0000-0000-000000000001` with a new name and `due_date: 2026-10-31` gave 422 on `due_date`, and the project read back unchanged | Pass |
| AC-INC07.6 | Access comes before validation: a Team Member, Viewer or Workspace Administrator sending a `null` owner or dates out of order gets 403; Dan gets 404. | `ProjectApiTest` (`accessIsCheckedBeforeValidationInsideTheWorkspace`, `accessIsCheckedBeforeValidationFromAnotherWorkspace`) | Pass |
| AC-INC07.7 | `created_by_member_id`, `is_archived` and `workspace_id` on `PATCH` give 422. | `ProjectApiTest.updateRefusesIdentityAndServerSetFields` | Pass |
| AC-INC07.8 | Existing data handled as decided; V4 applies cleanly; the database refuses a project without an owner or with its start date after its due date; startup succeeds after a restart. | Database agent's SQL check on the scratch databases `matriz_scratch` and `matriz_scratch2`: violations refused, a failed precondition rolls V4 back, V4 applies after the fix (accepted as the evidence 2026-11-16, review finding R-01). Read-only check of `matriz` on 2026-11-16: none of its 3 projects lacks an owner or has its start date after its due date. Startup of the new build on `matriz` (2026-11-16): V4 applied ("Successfully applied 1 migration", schema version 4) and the application started; after a restart, "Schema public is up to date", and a read-only comparison showed the 3 projects (id, name, owner, start date, due date) and the 5 tasks unchanged. | Pass |
| AC-INC07.9 | openapi.yaml 0.7.0-draft describes both rules, the examples and the changed schemas, and matches the API's behaviour. | Review of openapi.yaml against `ProjectService` and the tests (doc-keeper, 2026-11-16) | Pass |
| AC-INC07.10 | All automated tests pass; no access-rule change. | `mvn verify` with the application stopped and the test database `matriz_test` (2026-11-16): 124 tests, 0 failures, 0 errors, jar repackaged, BUILD SUCCESS; security audit (pass, 3 low findings); `AccessPolicy` and `SecurityConfig` unchanged | Pass |

**Not verified yet:** none. The refused `PATCH` on the running build, V4 on `matriz` with a restart, and `mvn verify` were checked on 2026-11-16 (AC-INC07.2, AC-INC07.5, AC-INC07.8, AC-INC07.10).

**Known gap:** *resolved 2026-11-16 by INC-06 part 3b.* The project form no longer offers "No owner", sends only a loaded member as owner and only the changed fields on edit, and shows a deactivated owner as "Former member", kept unless someone else is chosen. Before that, choosing "No owner", saving after the member list failed to load, or saving a project whose owner was deactivated showed "An owner is required." at the Owner field.

**Open findings:** review finding R-01 (no automated test of the V4 constraints), accepted as Minor on 2026-11-16. INC-07 audit S-01 (low): a body that cannot be read gets 422 before the permission check; this existed before INC-07. INC-07 audit S-02 (low, optional): no dedicated error response when the database refuses a row. INC-07 audit S-03 (low): no test with a deactivated Project Manager; skipped by the project decision-maker on 2026-11-16.

### Verification campaign (2026-11-16)

**Goal:** a QA check of the built MVP behaviour in nine scenarios, approved by the project decision-maker on 2026-11-16. Commands, evidence, findings and open questions are in [verification.md](verification.md). The increments' statuses are unchanged.

| Case | Scenario | Type | Status |
|---|---|---|---|
| 01 | Access | Both | Pass |
| 02 | Projects | Both | Pass |
| 03 | Task creation and persistence | Both | Pass |
| 04 | Validation | Both | Pass |
| 05 | Status change | Both | Pass; priority Not executed (F17, outside the initial scope) |
| 06 | Assignees | Both | Pass |
| 07 | Comments | Both | Pass |
| 08 | Authorization | Both | Pass |
| 09 | Deletion and error display | Both | Pass after the B-01 fix (failed before it) |

**Tests:** `VerificationApiTest`, 12 tests added (140 in total); `mvn verify -Dspring-boot.repackage.skip=true` on `matriz_test`: 0 failures, BUILD SUCCESS. The repackage step was skipped because a process the campaign did not start locks the jar.

**Bug B-01, fixed:** a success notice from an earlier action stayed visible above the task form when a later save failed. Fixed with Option A (the task form clears the notice when it is submitted; Option B not chosen). The regression check is a scripted manual browser check, because the stack has no JavaScript test tool.

**Reported, not fixed:** FR-19 overdue marking is not built (O-1 in [verification.md](verification.md#other-findings-not-fixed)), and O-2 to O-5.

## Status log

| Date | Increment | Change | Approved by |
|---|---|---|---|
| 2026-11-12 | INC-01 | Plan approved; project skeleton, migration `V1__initial_schema.sql`, seed command and `DatabaseSetupTest` created; all acceptance criteria verified. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-02 | Plan approved (session cookie, 8-hour idle timeout, scope limited to authentication and the two documented operations, provisional rules for PD-01, PD-03, PD-17, PD-20). Sign-in, profile, sign-out, `AccessPolicy`, `createTask` and `updateTaskStatus` implemented; 32 new automated tests and manual curl checks pass. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-03 | Plan approved (deletion with cascade, project name search, owner defaults to the creator, name 3–100 and description ≤ 1000 characters, any Project Manager edits or deletes, creator becomes a project member, page and size paging, a Northwind-only demo user, progress in the list). Project operations, migration `V2`, and the first user interface implemented; 18 new automated tests and a manual browser check pass. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-04 | Plan approved (no priority, optional status on create, title 3–150 and description ≤ 2000 characters, Portuguese status labels, filters by status and owner, provisional task permissions, task paths under the project, owner choices from workspace members). Task operations and the Tasks section implemented; 16 new automated tests (69 in total) and a manual browser check pass, with status changes persisting after a reload and a restart. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-05 | Feature A plan approved (Option 1: assignees besides the single owner; eligible = active project members; Project Managers assign; 409 for duplicates; primary key in migration `V3`), implemented, verified and confirmed. Feature B plan approved (comments 1 to 2000 characters trimmed, author and date from the server, oldest first, project members comment), implemented and verified. 15 new automated tests (84 in total) and manual browser checks pass, with assignments and comments persisting after a reload and a restart. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-06 | Plan approved (one screen at a time; `MATRIZ_API_BASE_URL` served as `GET /config.js`, open without a session, same-site paths only; email kept and password cleared after a failed sign-in; UI-A7; shared form parts). Screen 1, sign-in, implemented; 20 new automated tests (104 in total) and manual browser checks pass. Status set to In progress. | Project decision-maker |
| 2026-11-12 | INC-06 | Dashboard plan approved (Q1 B: per-project task counts by status from `listTasks` `status_counts`, no total across projects; Q2: My tasks, notifications, members, feedback and personal-data requests left out until they have API operations; Q3: every workspace name as text in E-01, no switcher, Workspace column kept; Q4: UI-A1 confirmed; Q5: first 10 projects with the API total, "Showing N of total" and "See all projects" in a footer; Q6 B: demo workspace Orchard Office with Eva Demo and Finn Demo; Q7: "+ New project" hidden for Workspace Administrators, provisional PD-03, once in the empty state). Screen 2, dashboard, implemented; no new automated tests (104 in total pass), manual browser checks and a security audit pass; open checks listed under INC-06. Status stays In progress. | Project decision-maker |
| 2026-11-12 | INC-06 | Screen 3 plan approved (revision 3: screen 3 is the project list, part 3a, and the project form, part 3b, which waits for INC-07; the Projects screen keeps "Loading projects…" and its current error state, UI-A4 not applied there; task counts by status in each row; a page past the end opens the last page, keeping the search; 20 projects per page, the dashboard keeps 10; API tests accepted as evidence for the role-specific checks). Part 3a, the Projects list, implemented; new automated tests (110 in total, `mvn verify` BUILD SUCCESS) and manual browser checks pass; open checks and questions listed under INC-06. Status stays In progress. | Project decision-maker |
| 2026-11-16 | INC-06 | Test `teamMemberListsAProjectTheyAreNotAMemberOf` added as the evidence for AC-INC06.24; 111 automated tests pass (`mvn test` on the test database). Status stays In progress. | Project decision-maker |
| 2026-11-12 | INC-07 | Plan approved (project owner required, with an empty owner refused; a start date after the due date refused with 422; `openapi.yaml` 0.7.0-draft, `ProjectService` checks, migration V4 with owner NOT NULL and CHECK start_date <= due_date). Status set to Planned. | Project decision-maker |
| 2026-11-16 | INC-07 | Built: `openapi.yaml` 0.7.0-draft; `ProjectService` checks (an owner sent as `null` and a start date after the due date refused with 422); migration `V4__project_owner_required_and_date_order.sql` (owner NOT NULL, check `project_date_order_ck`); `editRulesMatchCreate` changed and 13 tests added (124 in total, `mvn test` on the test database, 0 failures). The database agent's check of V4 on scratch databases accepted as the evidence for AC-INC07.8. Review: approve with minor changes after R-01 was accepted; security audit: pass, 3 low findings. `mvn test` accepted as the evidence for AC-INC07.10. Status set to Done. | Project decision-maker |
| 2026-11-16 | INC-07 | Open checks done: `mvn verify` with the application stopped (124 tests, 0 failures, BUILD SUCCESS); the running build applied V4 to the development database and started; refused `PATCH` requests (a `null` owner; a due date before the start date) gave 422 and changed nothing; after a restart the schema was up to date and the projects and tasks were unchanged. The same `mvn verify` run covers the `mvn verify` check open under INC-06. Status stays Done. | Project decision-maker |
| 2026-11-16 | INC-06 | Part 3b plan approved (QC1: an edit with no change sends nothing and opens the project with "Changes saved."; QC2: an incomplete or impossible date is refused with "Enter a valid date."; QC3: the Owner hint on create only; AC-INC06.40 withdrawn). Part 3b, the project form on `formKit` and `guarded`, implemented. Review: approve with minor changes; R-02 and R-04 fixed; R-01 (a save that finishes after leaving the form still opens the project), R-03 (a create 404 keeps "This project was not found.") and R-04 (a year that is not 4 digits is refused) decided. 4 tests added, including the one for the PD-10 decision of 2026-11-12 (QG4 in the plan: the list, detail and dashboard keep a deactivated owner's name); 128 in total, `mvn verify` BUILD SUCCESS. Manual browser checks pass; API tests accepted as evidence for the role-specific checks of AC-INC06.42 and the API side of AC-INC06.43; the two fictitious projects were deleted afterwards (read-only check: 3 projects, no orphan rows). Security audit: pass, 1 low finding. Open checks listed under INC-06. Status stays In progress. | Project decision-maker |
| 2026-11-16 | Verification campaign | QA verification plan approved. `VerificationApiTest` added with 12 tests (140 in total; `mvn verify` with the repackage step skipped, BUILD SUCCESS). All nine scenarios executed; the priority part of case 05 not executed (F17, outside the initial scope). B-01 found and fixed with Option A (Option B not chosen), with a manual regression check in the browser. FR-19 overdue marking reported as not built. Increment statuses unchanged. Details in [verification.md](verification.md). | Project decision-maker |
