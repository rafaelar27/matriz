# Matriz

## Presentation of the Matriz project

Matriz is a project and task management platform designed for work teams. It covers project and task management and progress tracking (known facts KF-01 to KF-06 in [docs/requirements.md](docs/requirements.md#known-facts)).

This repository currently holds the product documentation that serves as the single source of truth for future design and development.

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

The MVP serves four user types: Workspace Administrator, Project Manager, Team Member and Viewer. It includes 21 functional requirements: 18 Must have and 3 Should have. Details are in [docs/requirements.md](docs/requirements.md#functional-requirements).

| Area | Requirements |
|---|---|
| Access and workspace | FR-01 Sign up and sign in · FR-02 Workspace creation · FR-03 Invite and remove members · FR-04 Roles and permissions · FR-05 Read-only access |
| Projects | FR-06 Create, edit and archive projects · FR-07 Project members · FR-08 Project list |
| Tasks | FR-09 Create, edit and delete tasks · FR-10 Assign a responsible person · FR-11 Due dates · FR-12 Task status |
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
| [openapi.yaml](openapi.yaml) | Draft API specification for task creation and task status changes, API assumptions (API-Ax) |
| [docs/ui/README.md](docs/ui/README.md) | UI notes for the dashboard: screen elements (E-xx), navigation, UI assumptions (UI-Ax) |
| [docs/ui/dashboard-wireframe.html](docs/ui/dashboard-wireframe.html) | Low-fidelity dashboard wireframe |
| [docs/implementation-plan.md](docs/implementation-plan.md) | Implementation increments, their acceptance criteria and verification, status log |

## Getting started

The application is a single Spring Boot application (Java 21) using a local PostgreSQL database. Commands below are for PowerShell on Windows and are run from the repository root. There is no user interface yet; the API is used through HTTP requests (see Authentication below).

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

### 3. Install dependencies and build

```powershell
mvn -DskipTests package
```

### 4. Start the application (applies migrations)

```powershell
mvn spring-boot:run
```

Flyway applies any pending migrations from `src/main/resources/db/migration` at startup; on an empty database the log shows `Successfully applied 1 migration`, and on later starts `Schema "public" is up to date`. The application listens on port 8080. Stop it with `Ctrl+C`. Starting it again keeps all data.

### 5. Load the demo data

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=seed"
```

The seed command inserts the demo data and exits. It never deletes data, and running it again inserts nothing (`Demo data: 0 new rows inserted`). Normal startup never seeds.

### 6. Run the verification check

```powershell
mvn verify
```

Runs all automated tests. Expected result: `Tests run: 53, Failures: 0` and `BUILD SUCCESS`.

- `DatabaseSetupTest` checks the database configured in `.env`: it answers queries, every migration succeeded and all 11 tables from [docs/database.md](docs/database.md) exist. It changes no data.
- `AuthenticationTest`, `CsrfCookieTest`, `TaskAccessTest` and `ProjectApiTest` check sign-in, sign-out, the project operations and every access rule through API requests. They use the separate `matriz_test` database (`MATRIZ_TEST_DB_URL`, default `jdbc:postgresql://localhost:5432/matriz_test`), load the demo data there and roll back every change. Create that database once, as the `matriz` role:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U matriz -h localhost -d matriz -c "CREATE DATABASE matriz_test OWNER matriz"
```

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

### Projects in the browser

With the application running (step 4) and the demo data loaded (step 5), open <http://localhost:8080> and sign in with a demo account.

| Screen | What you can do | Who |
|---|---|---|
| Projects | See every project in your workspaces with workspace, owner, due date and progress ("2 of 8 tasks done"); search by part of the name; move between pages | Every member |
| + New project | Choose the workspace, name (3 to 100 characters), description (up to 1000), owner (you, unless you choose another member) and dates | Project Manager |
| Project detail | See all of the project's details | Every member |
| Edit project | Change the name, description, owner and dates | Any Project Manager of the workspace |
| Delete project | Confirm in a dialog; the project, its tasks with their comments and notifications, and its project members are deleted. This cannot be undone. | Any Project Manager of the workspace |

The same operations are in [openapi.yaml](openapi.yaml): `GET /projects?q=&page=&size=`, `POST /projects`, `GET`, `PATCH` and `DELETE /projects/{project_id}`, `GET /progress?project_id=` and `GET /workspaces/{workspace_id}/members`. Projects are stored in PostgreSQL, so they are still there after a reload or a restart. Restarting the application ends every session, so you sign in again.

### Demo users

All demo data is fictitious. Every demo account uses the password you set in `MATRIZ_DEMO_PASSWORD`. Ids are fixed, so requests can use them directly.

| Test | Sign in as | Request | Expected |
|---|---|---|---|
| Create a task for a project member | `ana@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000001/tasks` with owner `c0000000-0000-0000-0000-000000000003` (Ben) | 201 |
| Project in another workspace | `ben@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000003/tasks` ("Lab inventory") | 404 |
| Task in another workspace | `vera@example.com` | `PATCH /tasks/e0000000-0000-0000-0000-000000000005` | 404 |
| Viewer is read-only | `vera@example.com` | `POST /projects/d0000000-0000-0000-0000-000000000001/tasks` | 403 |
| Owner changes status | `ben@example.com` | `PATCH /tasks/e0000000-0000-0000-0000-000000000001` with `{"status":"Done"}` | 200 |
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

| Project | Workspace | Project members | Tasks |
|---|---|---|---|
| Website redesign | Acme Studio | Ana, Ben, Vera | 3: one overdue (In progress, due 2026-11-04), one To do, one Done; 2 comments |
| Trade fair stand | Acme Studio | Ana, Ben | 1 with no due date |
| Lab inventory | Northwind Lab | Carla, Ana | 1, with 1 comment |

Every member of a workspace can see all of its projects (FR-08), so access differences between users come from workspaces (NFR-01) and roles (FR-04), not from project membership.

## Current project status

- **Stage:** development. The specification documents remain drafts pending client validation.
- **Done:** initial analysis, functional proposal and MVP specifications. Architectural style decided on 2026-11-05 (modular monolith, see the [decision log](docs/constraints.md#decision-log)). Draft data model, ER diagram, API specification (authentication, projects, progress and two task operations), and dashboard wireframe. Technology stack chosen on 2026-11-12: Java 21 and Spring Boot 3, a static HTML/CSS/JS front end and PostgreSQL (see [Technology stack](docs/architecture.md#technology-stack)).
- **In progress:** development. The project setup (INC-01), authentication and access control (INC-02) and projects (INC-03) increments are done (see [docs/implementation-plan.md](docs/implementation-plan.md)): sign-in, profile, sign-out, the createTask and updateTaskStatus operations, and creating, listing, searching, viewing, editing and deleting projects. The user interface covers sign-in and projects. Hosting and other technical choices are still pending; see [pending technical decisions](docs/constraints.md#pending-technical-decisions).
- **Blocking next step:** the five assumptions that shape the MVP most (AQ3, AQ6, AQ7, AQ11, AQ17) were confirmed on 2026-10-29 (see the [decision log](docs/constraints.md#decision-log)). The remaining [pending product decisions](docs/constraints.md#pending-product-decisions) (PD-01 to PD-13 and PD-15 to PD-24) must be made before the features they affect are designed. While the client is unavailable, the project decision-maker makes them (KC-01).
- **Last updated:** 2026-11-12.
