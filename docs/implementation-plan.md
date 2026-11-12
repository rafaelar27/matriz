# Matriz — Implementation Plan

> **Status:** draft, created on 2026-11-12. It holds the increments planned so far; later increments are added when they are planned. Requirement IDs are defined in [requirements.md](requirements.md), user stories in [user-stories.md](user-stories.md), the stack in [architecture.md](architecture.md#technology-stack) and the data model in [database.md](database.md).

## Increments

| ID | Increment | Status |
|---|---|---|
| INC-01 | Project setup | Done (2026-11-12) |
| INC-02 | Authentication and access control | Done (2026-11-12) |
| INC-03 | Projects (first end-to-end flow) | Done (2026-11-12) |

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

## Status log

| Date | Increment | Change | Approved by |
|---|---|---|---|
| 2026-11-12 | INC-01 | Plan approved; project skeleton, migration `V1__initial_schema.sql`, seed command and `DatabaseSetupTest` created; all acceptance criteria verified. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-02 | Plan approved (session cookie, 8-hour idle timeout, scope limited to authentication and the two documented operations, provisional rules for PD-01, PD-03, PD-17, PD-20). Sign-in, profile, sign-out, `AccessPolicy`, `createTask` and `updateTaskStatus` implemented; 32 new automated tests and manual curl checks pass. Status set to Done. | Project decision-maker |
| 2026-11-12 | INC-03 | Plan approved (deletion with cascade, project name search, owner defaults to the creator, name 3–100 and description ≤ 1000 characters, any Project Manager edits or deletes, creator becomes a project member, page and size paging, a Northwind-only demo user, progress in the list). Project operations, migration `V2`, and the first user interface implemented; 18 new automated tests and a manual browser check pass. Status set to Done. | Project decision-maker |
