# Matriz

## Presentation of the Matriz project

Matriz is a project and task management platform designed for work teams. It covers project and task management and progress tracking (known facts KF-01 to KF-06 in [docs/requirements.md](docs/requirements.md#known-facts)).

This repository holds the product documentation, which is the single source of truth, and the application built from it.

## Problem it aims to solve

*Assumption (AQ1, AQ5, AQ14):* teams lose track of who is doing what and by when, because their work is scattered across email, chat and spreadsheets, or sits in project tools they find too complex. The result is unclear ownership, lost updates, and no single overview of status.

## Main objectives

Full definitions and their basis are in [docs/requirements.md](docs/requirements.md#objectives).

- **OBJ-01:** one place to organize team work.
- **OBJ-02:** an explicit owner and due date for every task.
- **OBJ-03:** visibility of progress and delays for managers and stakeholders.
- **OBJ-04:** simplicity, so that Matriz is usable without training.
- **OBJ-05:** validation with pilot teams before a wider launch.

## Features planned for the MVP

The MVP serves four user types: Workspace Administrator, Project Manager, Team Member and Viewer. It includes 22 functional requirements: 19 Must have and 3 Should have. Details are in [docs/requirements.md](docs/requirements.md#functional-requirements).

| Area | Requirements |
|---|---|
| Access and workspace | FR-01 Sign up and sign in · FR-02 Workspace creation · FR-03 Invite and remove members · FR-04 Roles and permissions · FR-05 Read-only access |
| Projects | FR-06 Create, edit and archive projects · FR-07 Project members · FR-08 Project list |
| Tasks | FR-09 Create, edit and delete tasks · FR-10 Assign a responsible person · FR-11 Due dates · FR-12 Task status · FR-22 Task assignees |
| Views | FR-13 Project task list · FR-14 "My tasks" · FR-15 Filter tasks |
| Collaboration and notifications | FR-16 Task comments · FR-17 In-app notifications |
| Progress tracking | FR-18 Project progress indicator · FR-19 Overdue highlighting |
| Support and privacy | FR-20 Send feedback · FR-21 Personal data requests |

## Documentation structure

| File | Contents |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Vision and objectives, user types, functional requirements (FR-xx), non-functional requirements (NFR-xx), MVP scope, features outside the initial scope |
| [docs/user-stories.md](docs/user-stories.md) | User stories (US-xx), priorities, acceptance criteria, edge cases (EC-xx), traceability to requirements |
| [docs/constraints.md](docs/constraints.md) | Known constraints (KC-xx), assumptions (AQ1–AQ33), security and privacy requirements (SP-xx), pending technical decisions (TD-xx), scope limitations and pending product decisions (PD-xx) |
| [docs/architecture.md](docs/architecture.md) | Architectural style (decided 2026-11-05), components (CMP-xx), requirement coverage, component diagram |
| [docs/database.md](docs/database.md) | Conceptual and logical data model, business rules (BR-xx), data-model assumptions (DA-xx) |
| [docs/database-diagram.md](docs/database-diagram.md) | Entity-relationship diagram of the data model |
| [openapi.yaml](openapi.yaml) | Draft API specification (authentication, projects, progress, tasks, task assignees and comments), API assumptions (API-Ax) |
| [docs/ui/README.md](docs/ui/README.md) | UI notes for the dashboard: screen elements (E-xx), navigation, UI assumptions (UI-Ax) |
| [docs/ui/dashboard-wireframe.html](docs/ui/dashboard-wireframe.html) | Low-fidelity dashboard wireframe |
| [docs/implementation-plan.md](docs/implementation-plan.md) | Implementation increments, their acceptance criteria and verification, status log |
| [docs/verification.md](docs/verification.md) | QA verification campaign: environment, commands, scenario results with evidence, automated tests, bugs found, limitations and open questions |

## Getting started

The application is a single Spring Boot application (Java 21) using a local PostgreSQL database. Commands below are for PowerShell on Windows and are run from the repository root. The user interface is served by the same application (see [User interface](#user-interface) below); the API can also be used directly through HTTP requests (see Authentication below).

### Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9 or later | `mvn -v` |
| PostgreSQL | 18 (tested); running on `localhost:5432` | `& "C:\Program Files\PostgreSQL\18\bin\pg_isready.exe" -h localhost` |

### 1. Create the database

Open psql as the PostgreSQL superuser (it asks for the password chosen when PostgreSQL was installed):

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost
```

Then create the application's role and database, choosing your own password, and leave with `\q`:

```sql
CREATE ROLE matriz WITH LOGIN CREATEDB PASSWORD 'your-own-password';
CREATE DATABASE matriz OWNER matriz;
```

### 2. Configure the environment

```powershell
cp .env.example .env
```

Edit `.env` and replace every value: `MATRIZ_DB_PASSWORD` is the role password from step 1, and `MATRIZ_DEMO_PASSWORD` is any fictitious password for the demo accounts. `.env` is ignored by Git and must never be committed. Environment variables set in the shell take precedence over `.env`.

| Variable | Purpose |
|---|---|
| `MATRIZ_DB_URL` | JDBC URL of the database, for example `jdbc:postgresql://localhost:5432/matriz` |
| `MATRIZ_DB_USER` | Database role |
| `MATRIZ_DB_PASSWORD` | Database role password |
| `MATRIZ_DEMO_PASSWORD` | Password given to every demo account by the seed command (stored only as a BCrypt hash) |
| `MATRIZ_PORT` | Optional HTTP port, default `8080` |
| `MATRIZ_TEST_DB_URL` | JDBC URL of the separate test database used by `mvn verify`, default `jdbc:postgresql://localhost:5432/matriz_test` (same role and password) |
| `MATRIZ_SESSION_TIMEOUT` | Optional sign-in session idle timeout, default `8h` (for example `30m` or `1m`) |
| `MATRIZ_COOKIE_SECURE` | Optional; `true` sends the session cookie only over HTTPS. Default `false` for local HTTP use |
| `MATRIZ_API_BASE_URL` | Optional base URL of the API for the user interface (property `matriz.api-base-url`). Default empty: the same server. Only a path on the same site is accepted, such as `/api` behind a proxy; any other value, such as another host, a scheme, a query or spaces, stops the application at startup, because the session and CSRF cookies are same-site only (TD-03) |

### 3. Install dependencies and build

```powershell
mvn -DskipTests package
```

### 4. Start the application (applies migrations)

```powershell
mvn spring-boot:run
```

Flyway applies any pending migrations from `src/main/resources/db/migration` at startup; on an empty database the log shows `Successfully applied 4 migrations`, and on later starts `Schema "public" is up to date`. The application listens on port 8080. Stop it with `Ctrl+C`. Starting it again keeps all data. On a database that already has migrations V1 to V3, the next start applies V4, which stops startup if any project has no owner or a start date after its due date.

### 5. Load the demo data

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=seed"
```

The seed command inserts the demo data and exits. It never deletes data, and running it again inserts nothing (`Demo data: 0 new rows inserted`). Normal startup never seeds. On a database seeded before 2026-11-12, running it once more adds only the Orchard Office demo data (5 new rows).

### 6. Run the verification check

```powershell
mvn verify
```

Runs all automated tests. Expected result: `Tests run: 276, Failures: 0` and `BUILD SUCCESS`. The coverage report (JaCoCo) is written to `target/site/jacoco/index.html` after `mvn verify`; `mvn test` does not write it, and no coverage threshold is set.

- `DatabaseSetupTest` checks the database configured in `.env`: it answers queries, every migration succeeded and all 11 tables from [docs/database.md](docs/database.md) exist. It changes no data.
- `AuthenticationTest`, `CsrfCookieTest`, `TaskAccessTest`, `ProjectApiTest`, `TaskApiTest`, `AssignmentApiTest`, `CommentApiTest`, `ClientConfigApiTest` and `VerificationApiTest` check sign-in, sign-out, the project, task, assignee and comment operations, the open `GET /config.js` and every access rule through API requests; `VerificationApiTest` holds the API checks of the QA verification campaign ([docs/verification.md](docs/verification.md)). They use the separate `matriz_test` database (`MATRIZ_TEST_DB_URL`, default `jdbc:postgresql://localhost:5432/matriz_test`), load the demo data there and roll back every change (the one exception is `TaskIntegrationTest`, described below). Create that database once, as the `matriz` role:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U matriz -h localhost -d matriz -c "CREATE DATABASE matriz_test OWNER matriz"
```

- `ClientConfigControllerTest` is a unit test without a database: it checks which `MATRIZ_API_BASE_URL` values are accepted or stop startup, and that an accepted value is safely encoded in `/config.js`.
- `TaskTitleRuleTest` is a unit test of the task title rule, with no Spring and no database; it lives in `src/test/java/com/matriz/tasks`.
- `AccessPolicyTest`, `RoleTest`, `TaskStatusRuleTest`, `TaskDescriptionRuleTest`, `TaskEntityTest`, `ApiExceptionTest`, `ApiExceptionHandlerTest` and `IdsTest` are also unit tests, with no Spring and no database.
- `TaskIntegrationTest` sends real HTTP requests with a real sign-in, using the CSRF cookie and header; nothing is mocked. It commits its data in the `matriz_test` database, deletes the tasks it creates after each test, and leaves the demo data seeded. Run it alone with the variables below set in the environment (fictitious placeholders):

```bash
export MATRIZ_TEST_DB_URL="jdbc:postgresql://localhost:5432/matriz_test"
export MATRIZ_DB_USER="matriz"
export MATRIZ_DB_PASSWORD="<role password>"
mvn test -Dtest=TaskIntegrationTest
```

  While `mvn verify` runs, `MATRIZ_DB_URL` must also point at the test database (the same URL as `MATRIZ_TEST_DB_URL`), so that `DatabaseSetupTest` never touches the development database.

### Authentication

Matriz uses a **server-side session cookie** (TD-03, decided 2026-11-12). The API contract is in [openapi.yaml](openapi.yaml).

| Step | Request | Result |
|---|---|---|
| 1. Get a CSRF token | `GET /auth/csrf` | Sets the `XSRF-TOKEN` cookie |
| 2. Sign in | `POST /auth/sign-in` with `{"email": "...", "password": "..."}` and header `X-XSRF-TOKEN` | 200 with the profile; sets a new `MATRIZ_SESSION` cookie (HttpOnly, SameSite=Lax) and a new `XSRF-TOKEN` |
| 3. Use the API | Send the `MATRIZ_SESSION` cookie; requests that change data also send `X-XSRF-TOKEN` | Without a session: 401 `not_authenticated`; without the CSRF header: 403 `csrf_invalid` |
| 4. Profile | `GET /auth/me` | The account and its active memberships with roles |
| 5. Sign out | `POST /auth/sign-out` with `X-XSRF-TOKEN` | 204; the session is destroyed on the server |

**Session lifecycle:** created at sign-in (always a new session id); kept in memory on the server; expires after **8 hours without requests** (`MATRIZ_SESSION_TIMEOUT`, for example `30m` or `1m`); ended by sign-out or by restarting the application. A request with an ended or expired session gets 401 `session_expired` ("Your session has ended. Sign in again."). Passwords are stored only as BCrypt hashes and never appear in responses or logs. A wrong password and an unknown email get the same response: 401 `invalid_credentials`.

**Who may do what:** the backend reads the caller's identity only from the session, and their role and status from the database on every request. A project or task in another workspace returns 404, as if it did not exist; a refused action inside the caller's own workspace returns 403. Some rules are **provisional** until their pending decisions are made: Workspace Administrators cannot create tasks (PD-03), Team Members can only create tasks they own and only owners change a task's status (PD-01), Viewers are refused even on tasks they own (PD-17), and only project members can create tasks in a project (PD-20).

**Try it with curl** (Git Bash or any shell with `curl`; replace `your-demo-password` with your `MATRIZ_DEMO_PASSWORD`):

```bash
curl -s -c jar.txt http://localhost:8080/auth/csrf
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar.txt)
curl -s -b jar.txt -c jar.txt -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"email":"ben@example.com","password":"your-demo-password"}' http://localhost:8080/auth/sign-in
curl -s -b jar.txt http://localhost:8080/auth/me
```

### User interface

The user interface is static HTML, CSS and JavaScript in `src/main/resources/static`, served by the application itself; there is no separate front-end build or server. Start the application (step 4) and open <http://localhost:8080>.

The interface finds the API through `GET /config.js`, which the application generates from `MATRIZ_API_BASE_URL` and which only `static/js/app.js` reads. Leave the variable empty to use the server that serves the page. Set it to a path such as `/api` only when a proxy on the same site forwards that path to the application. In Git Bash, set `MSYS_NO_PATHCONV=1` when passing `MATRIZ_API_BASE_URL` on the command line, or the shell rewrites `/api` into a Windows path. `GET /config.js` is open without a session and holds no secret; it is not part of the API contract.

**Sign in** (FR-01, US-01):

- A missing email or password, or an email without "@", shows a message at the field and moves the focus to the first one.
- A wrong email or password shows "The email or password is incorrect."; the email is kept and the password is cleared. Repeated clicks send a single request.
- If Matriz cannot be reached: "Matriz could not be reached. Check your connection and try again."
- When the session ends, the interface returns to sign-in with "Your session has ended. Sign in again." The screen you were on is reopened only right after signing in, and only if the same account signs in; another account starts at the first screen, the dashboard (UI-A1, UI-A7). After a full page reload the earlier account is not known, so the screen is kept and the API still decides access.
- Opening sign-in while already signed in always goes to the dashboard. Signing out shows "You have signed out." and forgets the earlier screen.

**Dashboard** (`#/dashboard`; FR-08, FR-13, FR-18; [docs/ui/README.md](docs/ui/README.md#dashboard)) is the first screen after signing in, and the screen an unknown address opens (UI-A1):

- The top bar shows the names of every workspace you belong to, as text (no workspace switcher, PD-16), and the main menu **Dashboard** and **Projects**.
- The **Projects** section shows the first 10 of your projects with workspace, owner, due date, progress ("3 of 12 tasks done") and the number of tasks per status (Por fazer, Em curso, Concluída), the total number of projects, "Showing N of total" and **See all projects**. There is no total across projects (F41 is outside the initial scope).
- With no projects it shows "No projects yet."; Project Managers also see **+ New project** there, once. Workspace Administrators do not see it (provisional, PD-03).
- A section that cannot be loaded shows "This section could not be loaded." and **Try again**; a project whose task counts cannot be loaded shows "—" in its row.
- My tasks, notifications, members, feedback and personal-data requests are not on the dashboard yet, because they have no API operations.

**Projects** (`#/projects`; FR-08, FR-13, FR-18, US-28) opens from **Projects** in the main menu and from **See all projects** on the dashboard:

- It lists every project in your workspaces, 20 per page with **Previous** and **Next**; each project appears on exactly one page. The dashboard keeps showing 10.
- Each row shows workspace, owner, due date, progress and the number of tasks per status (Por fazer, Em curso, Concluída); a project without tasks shows "0 of 0 tasks done". There is no total across projects (F41 is outside the initial scope).
- Search by part of the project name, ignoring case; a search with no match shows a message that no projects match, and a search of only spaces lists every project. The search field takes at most 100 characters.
- An address with a page past the last opens the last page that has projects, keeping the search; **Back** does not return to the empty page.
- With no projects it shows "No projects yet."; Project Managers also see **+ New project** there, once. Workspace Administrators do not see it (provisional, PD-03).
- While the list loads it shows "Loading projects…"; if it cannot be loaded, "This section could not be loaded." and **Try again**, which loads the list again. The dashboard's loading wording and single-load guard (UI-A4) are not applied on this screen.

### Projects in the browser

With the application running (step 4) and the demo data loaded (step 5), open <http://localhost:8080> and sign in with a demo account.

| Screen | What you can do | Who |
|---|---|---|
| Projects | See every project in your workspaces with workspace, owner, due date, progress ("2 of 8 tasks done") and the number of tasks per status; search by part of the name; move between pages of 20 | Every member |
| + New project | Choose the workspace, name (3 to 100 characters), description (up to 1000), owner (you, unless you choose another active member; a project always has an owner) and dates (the start date cannot be after the due date) | Project Manager |
| Project detail | See all of the project's details | Every member |
| Edit project | Change the name, description, owner and dates; the owner cannot be removed, and the start date cannot be after the due date. An owner who is no longer an active member shows as "Former member" and stays the owner unless you choose someone else. Saving without changes sends nothing. | Any Project Manager of the workspace |
| Delete project | Confirm in a dialog; the project, its tasks with their comments and notifications, and its project members are deleted. This cannot be undone. | Any Project Manager of the workspace |

The same operations are in [openapi.yaml](openapi.yaml): `GET /projects?q=&page=&size=`, `POST /projects`, `GET`, `PATCH` and `DELETE /projects/{project_id}`, `GET /progress?project_id=` and `GET /workspaces/{workspace_id}/members`. Projects are stored in PostgreSQL, so they are still there after a reload or a restart. Restarting the application ends every session, so you sign in again.

### Tasks in the browser

Open a project from the project list. Its **Tasks** section lists the project's tasks with owner, due date and status, the number of tasks per status, and pages of 10.

| Action | How | Who (provisional rules, see [openapi.yaml](openapi.yaml) `updateTask`) |
|---|---|---|
| Filter | Choose a **Status** and/or an **Owner**; both combine with the pages, and the choice stays in the address after a reload | Every member |
| View | Click a task's title | Every member |
| + New task | Title (3 to 150 characters, not only spaces), description (up to 2000), owner, due date, status (Por fazer by default) | Project Managers and Team Members who are members of the project; Team Members own the tasks they create |
| Edit | **Edit** on the row | Project Managers, or the owner if a Team Member; only Project Managers change the owner |
| Change status | The status list on the row | The task's owner |
| Delete | **Delete** on the row, then confirm; the task's comments and notifications are deleted with it | The member who created the task |

Statuses are shown as **Por fazer**, **Em curso** and **Concluída**, but the API always uses `To do`, `In progress` and `Done`. After every change the list, the counts and the project's progress update without reloading the page. Task priority is not part of the MVP (F17).

**Try it:** sign in as `ana@example.com`, open "Website redesign", create a task without choosing a status (it shows Por fazer), change "Approve page layout" to Em curso, filter by Em curso, reload the page, then restart the application and sign in again: the change is still there. Sign in as `dan@example.com` and open `#/projects/d0000000-0000-0000-0000-000000000001`: the project is not found. The same operations through the API: `GET` and `POST /projects/{project_id}/tasks?status=&owner_member_id=&page=&size=`, and `GET`, `PATCH`, `DELETE /projects/{project_id}/tasks/{task_id}`.

### Assignees and comments

Click a task's title to open its details. Below the description are two areas.

| Area | What you see and do | Who |
|---|---|---|
| **Assignees** | Members working on the task besides its owner, with the time each was assigned. Tick one or more members and choose **Assign selected**; **Remove** takes one assignee off. Only active members of the task's project are offered, and the owner does not change. | Everyone reads; only Project Managers assign and remove (provisional, PD-01) |
| **Comments** | Every comment, oldest first, with author and date. Type up to 2000 characters and choose **Post comment**; an empty comment or one of only spaces is refused. Comments cannot be edited or deleted. | Everyone reads; Project Managers and Team Members who are members of the project post (provisional) |

Author, dates and eligibility are always decided by the backend. Assigning the same member twice gives **409** (the database key also prevents it); a member outside the project gives **422**; a comment that sends its own author or date gives **422**.

**Try it:** sign in as `ana@example.com`, open "Website redesign", click "Approve page layout", assign Ben and Vera, remove Ben, then reload the page and restart the application: Vera is still assigned. Click "Review homepage images" and post a comment; it appears last with your name and the time. The API: `GET /projects/{project_id}/tasks/{task_id}/eligible-assignees`, `GET` and `POST …/assignees` (`{"member_ids": [...]}`), `DELETE …/assignees/{member_id}`, and `GET` and `POST …/comments` (`{"body": "..."}`).

### Demo users

All demo data is fictitious. Every demo account uses the password you set in `MATRIZ_DEMO_PASSWORD`. Ids are fixed, so requests can use them directly.

| Test | Sign in as | Request | Expected |
|---|---|---|---|
| Create a task for a project member | `ana@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000001/tasks` with owner `c0000000-0000-0000-0000-000000000003` (Ben) | 201 |
| Project in another workspace | `ben@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000003/tasks` ("Lab inventory") | 404 |
| Task in another workspace | `vera@example.com` | `PATCH /projects/d0000000-0000-0000-0000-000000000003/tasks/e0000000-0000-0000-0000-000000000005` (Lab inventory, Northwind Lab) | 404 |
| Viewer is read-only | `vera@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000001/tasks` | 403 |
| Owner changes status | `ben@example.com` | `PATCH /projects/d0000000-0000-0000-0000-000000000001/tasks/e0000000-0000-0000-0000-000000000001` with `{"status":"Done"}` | 200 |
| Non-owner changes status | `ana@example.com` | the same request | 403 (provisional, PD-01) |
| Owner not in the project | `ana@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000002/tasks` with owner `c0000000-0000-0000-0000-000000000004` (Vera) | 422 |
| Spoofed creator | `ben@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000002/tasks` with `created_by_member_id` | 422 |

| Email | Workspace and role | Use it to test |
|---|---|---|
| `ana@example.com` | Acme Studio: Project Manager · Northwind Lab: Team Member | The same person with different roles in two workspaces; a Project Manager in the shared project "Website redesign" |
| `ben@example.com` | Acme Studio: Team Member | A Team Member in the shared project; owns an overdue task; **cannot access** "Lab inventory" (another workspace, NFR-01) |
| `vera@example.com` | Acme Studio: Viewer | Read-only access (FR-05) in the shared project; **cannot access** "Lab inventory" |
| `carla@example.com` | Acme Studio and Northwind Lab: Workspace Administrator | Member and role management (FR-03, FR-04); cannot create, edit or delete projects (provisional, PD-03) |
| `dan@example.com` | Northwind Lab: Project Manager | Creates and edits Northwind projects; **sees no** Acme Studio project (NFR-01) |
| `eva@example.com` | Orchard Office: Project Manager | A workspace without projects: the dashboard shows "No projects yet." with **+ New project**; **sees no** Acme Studio or Northwind Lab project (NFR-01). Creating a project as Eva ends the empty state; deleting it restores it |
| `finn@example.com` | Orchard Office: Workspace Administrator | The same empty dashboard without **+ New project** (provisional, PD-03); keeps an administrator in Orchard Office (SP-07) |

| Project | Workspace | Project members | Tasks |
|---|---|---|---|
| Website redesign | Acme Studio | Ana, Ben, Vera | 3: one overdue (In progress, due 2026-11-04), one To do, one Done; 2 comments |
| Trade fair stand | Acme Studio | Ana, Ben | 1 with no due date |
| Lab inventory | Northwind Lab | Carla, Ana | 1, with 1 comment |

Orchard Office has no projects on purpose, to show the empty state.

Every member of a workspace can see all of its projects (FR-08), so access differences between users come from workspaces (NFR-01) and roles (FR-04), not from project membership.

## Current project status

- **Stage:** development. The specification documents remain drafts pending client validation.
- **Done:** initial analysis, functional proposal and MVP specifications. Architectural style decided on 2026-11-05 (modular monolith, see the [decision log](docs/constraints.md#decision-log)). Draft data model, ER diagram, API specification (authentication, projects, progress, tasks, assignees and comments), and dashboard wireframe. Technology stack chosen on 2026-11-12: Java 21 and Spring Boot 3, a static HTML/CSS/JS front end and PostgreSQL (see [Technology stack](docs/architecture.md#technology-stack)).
- **In progress:** development. The project setup (INC-01), authentication and access control (INC-02), projects (INC-03), tasks (INC-04), task collaboration (INC-05) and project owner required and date order (INC-07) increments are done (see [docs/implementation-plan.md](docs/implementation-plan.md)): sign-in, profile, sign-out, creating, listing, searching, viewing, editing and deleting projects, creating, listing, filtering, viewing, editing and deleting tasks and changing their status, assigning members to tasks, and commenting on tasks; every project has an owner, and its start date is not after its due date. The user interface covers sign-in, the dashboard, projects, tasks, assignees and comments, and is being reworked one screen at a time (INC-06, in progress; the sign-in and dashboard screens, the project list and the project form are done). Hosting and other technical choices are still pending; see [pending technical decisions](docs/constraints.md#pending-technical-decisions).
- **Blocking next step:** the five assumptions that shape the MVP most (AQ3, AQ6, AQ7, AQ11, AQ17) were confirmed on 2026-10-29 (see the [decision log](docs/constraints.md#decision-log)). The remaining [pending product decisions](docs/constraints.md#pending-product-decisions) (PD-01 to PD-13, PD-15 to PD-20 and PD-22 to PD-24; PD-04, PD-10 and PD-13 in part) must be made before the features they affect are designed. While the client is unavailable, the project decision-maker makes them (KC-01).
- **Last updated:** 2026-11-16.
