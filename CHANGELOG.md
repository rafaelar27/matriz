# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/). This project follows Semantic Versioning (decision log 2026-11-19). Each release has an annotated Git tag `v<version>` and release notes in `docs/release-notes-<version>.md`. The entries come from the Git history and [docs/implementation-plan.md](docs/implementation-plan.md); increments are named INC-xx, with the date they were recorded.

## [Unreleased]

Nothing yet.

## [0.3.0] - 2026-11-22

Release candidate; no production release is published. Includes INC-17 to INC-19, made after the `v0.2.0` tag.

### Added
- Invitations by email (INC-17; FR-03, FR-04, US-03; `openapi.yaml` 0.13.0-draft): `createInvitation`, `listInvitations`, `cancelInvitation`, `listMyInvitations` and `acceptInvitation`. Invitation emails go out over SMTP with Spring Mail (TD-04, decided 2026-11-19); without `MATRIZ_SMTP_HOST` the application only logs that an invitation was created, without the address; a failed send answers 503 `email_not_sent` and stores nothing. Accepting needs a signed-in account with the matching email, reactivates a deactivated member and cancels other pending invitations to the same address. Migration V6. New optional variables `MATRIZ_SMTP_HOST`, `MATRIZ_SMTP_PORT`, `MATRIZ_SMTP_USERNAME`, `MATRIZ_SMTP_PASSWORD`, `MATRIZ_SMTP_STARTTLS`, `MATRIZ_SMTP_FROM` and `MATRIZ_PUBLIC_URL` in `.env.example`.
- Edit a workspace (INC-18; FR-23, FR-04, FR-03, US-04, US-30, US-31): `updateWorkspace`, `updateMemberRole` and `removeMember` (permanent, replacing deactivation). The last active Workspace Administrator cannot be removed or demoted (SP-07, EC-03). Removed authors show as "Former member". Migration V7.
- Delete a workspace, soft (INC-19; FR-24, US-32): `deleteWorkspace` with `confirm_name`; a deleted workspace answers 404 everywhere and its data is kept. Migration V8.
- Interface: Members screen, Rename, Delete and Leave dialogs, pending invitations with Accept.

### Changed
- `pom.xml`: adds `spring-boot-starter-mail`; version 0.2.0 to 0.3.0.

### Notes
- The PR #26 commit message reports 707 of 708 tests passing, with one failure also present on `master` and fixed by PR #23; `mvn verify` on 2026-11-22 passed 708 of 708 (see `docs/delivery-report.md`).

## [0.2.0] - 2026-11-19

First tagged version (annotated tag `v0.2.0`, created on the merge commit of the release pull request after the merge). Matriz follows Semantic Versioning in its initial development phase (0.y.z); the minor number is raised from the never-released `0.1.0-SNAPSHOT`. It is a candidate: it is not published to production, and a first deployment is reported (AGENTS.md, docs/implementation-plan.md) but has not been validated; no public check is recorded. The API contract (`openapi.yaml`) has its own version, 0.12.0-draft, independent of this one. See [docs/release-notes-0.2.0.md](docs/release-notes-0.2.0.md) for what is included and the known limitations.

### Added
- INC-01 (2026-11-12): project skeleton (one package per component, CMP-01 to CMP-13), Flyway migration `V1__initial_schema.sql` with the 11 entities of `docs/database.md`, the idempotent demo-data seed command (Spring profile `seed`) and `DatabaseSetupTest`.
- INC-02 (2026-11-12): sign-in, profile (`GET /auth/me`) and sign-out with a server-side session cookie, CSRF protection, BCrypt password hashes and an 8-hour idle timeout (TD-03); every operation authorized through one policy (`AccessPolicy`, CMP-03).
- INC-03 (2026-11-12): projects (create, list with search by name and paging, view, edit, delete), the progress indicator (`GET /progress`), reading a workspace's active members, migration `V2` and the first user interface.
- INC-04 (2026-11-12): tasks within a project (create, list filtered by status and owner with paging, view, edit, delete, status change) and the Tasks section of the interface.
- INC-05 (2026-11-12): task assignees (`eligible-assignees`, assign, remove; a duplicate assignment gives 409; migration `V3`) and task comments (1 to 2000 characters, author and date set by the server).
- INC-06 (in progress): the user interface is reworked one screen at a time. Done so far: sign-in, the dashboard, the Projects list and the project form; the open `GET /config.js` and the `MATRIZ_API_BASE_URL` setting; demo data for Dan (Northwind Lab only) and for the workspace Orchard Office (Eva and Finn).
- INC-07 (2026-11-16): a project always has an owner and its start date is not after its due date; migration `V4` with a required owner and the check `project_date_order_ck`; `openapi.yaml` 0.7.0-draft.
- QA verification campaign (2026-11-16): `VerificationApiTest` (12 tests) and [docs/verification.md](docs/verification.md).
- Release review (2026-11-16 and 2026-11-17): implementation notes AR-01 to AR-06 in `docs/architecture.md`, and `openapi.yaml` 0.8.0-draft.
- `docs/test-plan.md` (2026-11-17) and the tests derived from it: additions to `TaskApiTest` and the real-HTTP `TaskIntegrationTest`.
- INC-09 (2026-11-17): JaCoCo 0.8.14 coverage measurement (`target/site/jacoco` at `mvn verify`, no threshold) and Spring-free unit tests for the access rules and for task, error and id rules.
- INC-10 (2026-11-18): unit tests for the project, comment, assignment and task-owner rules, after a behaviour-preserving refactoring of four service files.
- One Playwright end-to-end test in `e2e/` (sign in, open a project, create a task) with a guard that refuses a non-local host.
- `docs/review.md` (2026-11-18): code and access-control review, with regression tests (`PageParamsTest`, `AuthControllerSignInTest`).
- Public sign-up (INC-12; FR-01, US-22): `POST /auth/sign-up` creates a personal account and signs it in; the sign-up and welcome screens; migration V5 (unique index on `lower(email)`); rate limits with 429 and `Retry-After` on sign-up and sign-in (NFR-11); a 16 KB body limit on `/auth/` routes (413); `MATRIZ_RATE_*` variables; `openapi.yaml` 0.9.0-draft; decision-log entries of 2026-11-18 and 2026-11-19 (SP-08 to SP-10, PD-25, PD-26, TD-13, BR-24).
- `Dockerfile` and `.dockerignore`: multi-stage image of the Spring Boot application (non-root user).
- `compose.yaml`: PostgreSQL 16 and the application, with healthchecks, a named data volume and an on-demand, idempotent `seed` service.
- `.github/workflows/ci.yml`: `test` (`mvn verify` on a PostgreSQL service), `build` (`mvn -B -DskipTests package`), `docker` (image build, not published) and `e2e` (Compose stack, seed, Playwright); `docker` and `e2e` need both `test` and `build`.
- README sections "Run with Docker", "Continuous integration" and "End-to-end test"; decision-log entries of 2026-11-18 for Docker, Compose and CI, and of 2026-11-19 for the hosting choice (TD-02 decided in part: Render and Neon, EU region, single service; a first deployment exists but has not been validated, and no public check is recorded; TD-05, TD-06 and TD-13 stay pending).
- Neon connection for local development (PR #19): `storage/NeonDatabaseEnvironment` derives `MATRIZ_DB_URL`, `MATRIZ_DB_USER` and `MATRIZ_DB_PASSWORD` from `DATABASE_URL_UNPOOLED` in `.env.local` when `MATRIZ_DB_URL` is not set; the derived URL always uses `sslmode=verify-full` with `DefaultJavaSSLFactory`; `NeonDatabaseEnvironmentTest`. Node tooling was added at the repository root: `neon.ts`, `package.json` and `package-lock.json` with `@neon/config` and `@neon/env`. It is not part of the Maven build or the Docker image.
- `createWorkspace` (`POST /workspaces`, FR-02, US-02; INC-14, `openapi.yaml` 0.10.0-draft, now 0.11.0-draft with INC-15): any signed-in account can create a workspace and becomes its first member, a Workspace Administrator, in the same transaction; the name is trimmed, 3 to 100 characters, with no NUL character (refused with 422), duplicates allowed. The interface gets a "New workspace" link and form. 39 automated tests added (`WorkspaceApiTest` 19, `WorkspaceNameRuleTest` 19, `WorkspaceCreateRollbackTest` 1); decision-log entry of 2026-11-19 (PD-16 in part). INC-14 is Done.
- Workspace Administrators can create, edit and delete the projects of their workspace, like a Project Manager (INC-15, `openapi.yaml` 0.11.0-draft; PD-03 decided in part, decision log 2026-11-19). `AccessPolicy.checkManageProject` and the interface ("+ New project" and the create and edit forms) changed; task, assignee and comment rules for Workspace Administrators are unchanged. `ProjectApiTest` renamed `onlyProjectManagersCreateEditAndDelete` to `teamMemberAndViewerCannotCreateEditAndDelete` and gained Workspace Administrator tests; `WorkspaceCreateRollbackTest` added. The suite is 388 tests. INC-15 is Done.
- Workspace Administrators work on tasks in a limited way (INC-16, `openapi.yaml` 0.12.0-draft; PD-03 decided in part, decision log 2026-11-19): in projects they are members of they create tasks (choosing the owner) and comment; they assign and unassign members and change a task's owner; they change the status of tasks they own and delete tasks they created. They do not edit a task's title, description or due date. `AccessPolicy`, the interface and the contract changed; the rules for Viewers, Team Members and Project Managers are unchanged. Task, assignee, comment and project tests updated and Workspace Administrator tests added; the suite is 402 tests. INC-16 is Done.
- Workspaces screen (E-22, FR-02, FR-03, US-02; decision log 2026-11-19): the main menu shows "Workspaces" instead of "New workspace". The screen lists every workspace the member belongs to, Workspace Administrator rows first and then by name, with the member's role and, on request, the active members (`listWorkspaceMembers`). "+ New workspace" (E-21) moves beside the Dashboard heading. No API change; rename, delete, invite and leave have no API operation yet and are not shown.
- Deployment guide for Render and Neon (`docs/deployment.md`) and `render.yaml`; hosting decision TD-02 recorded in the decision log on 2026-11-19 (PR #20).
- Test suite: 535 automated tests (`mvn verify` on `matriz_test`, release verification run 2026-11-19, 0 failures).

### Changed
- INC-11 (2026-11-18): the interface shows task statuses in English (To do, In progress, Done), equal to the API values, instead of Portuguese labels; the decision-log entry of 2026-11-18 supersedes that of 2026-11-12.
- A task's owner must be an active member of the task's project, on create and on edit; otherwise 422 on `owner_member_id` (review finding S-07, decision-log entry of 2026-11-18).
- The `createTask` `Location` header is the project-scoped path `/projects/{project_id}/tasks/{task_id}`, as in `openapi.yaml`.
- Page and size parsing of the two list endpoints is shared in `shared/PageParams` (release review, no behaviour change).
- Names refuse control and invisible formatting characters, emails are printable ASCII only (at most 254 characters), and `spring.mvc.formcontent.filter.enabled=false` (INC-12, security audits).
- Sign-in matches the email ignoring letter case and surrounding spaces, refuses passwords over 72 bytes with the generic 401, and counts failed attempts before the password is checked (INC-12).
- `server.forward-headers-strategy=none` is set explicitly, so forwarded headers are not trusted until TD-13 is decided (INC-12, security audit S-10).
- The interface uses the visual design approved on 2026-11-19 (docs/ui-design.md): colour tokens, Inter and Space Grotesk (self-hosted in `static/css/fonts`, SIL Open Font License), logo, status labels and a progress bar next to "N of M tasks done" (INC-13).
- `Dockerfile`: both base images (`maven:3.9.11-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) are pinned by tag and sha256 digest.
- `.dockerignore`: keeps secrets and local files out of the build context; since the hardening below it is an allow-list (the Flyway `.sql` migrations are kept).
- `compose.yaml`: all three services (`db`, `app`, `seed`) use an explicit, dedicated network `matriz-net` instead of the implicit default network; the pinned `postgres` image has a comment. Compose was run end to end on 2026-11-18 (see `docs/verification.md`, "Docker Compose").
- `.github/workflows/ci.yml`: a `build` job was added, `compose.yaml` is validated, and actions are pinned by commit SHA.
- pgJDBC overridden to 42.7.14 in `pom.xml` (`postgresql.version`; advisory GHSA-j92g-9f8w-j867, fixed in 42.7.12). Tests set `matriz.neon.enabled=false` (Surefire), so they never use the Neon connection.

### Removed
- INC-08 (2026-11-17): the unscoped route `PATCH /tasks/{task_id}` (`updateTaskStatus`) and its schema `TaskStatusUpdateRequest`; a task's status changes through the project-scoped `updateTask`. The regression test `TaskAccessTest.unscopedStatusRouteIsGone` was added.

### Fixed
- B-01 (2026-11-16): a success notice from an earlier action stayed visible above the task form when a later save failed.
- Review S-01 (2026-11-18): list pages whose offset overflows are refused with 422.
- Review S-05 (2026-11-18): an internal failure during sign-in is no longer reported as wrong credentials (401 `invalid_credentials`).
- `AuthControllerSignInTest` clears `SecurityContextHolder` after each test, which removes an order-dependent failure seen on Windows (PR #23; test-only).

### Security
- Container hardening after the security audit: root-owned application jar, `no-new-privileges` and `cap_drop: [ALL]` for `app` and `seed` in `compose.yaml`, `postgres:16-bookworm` pinned by digest in `compose.yaml` and CI, and an allow-list `.dockerignore`.
- The README `curl` example keeps the session cookie jar outside the repository and no longer puts the password on the command line (audit finding S-04, 2026-11-17).
