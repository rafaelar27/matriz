# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/). No version has been released and no Git tag exists, so every change is listed under Unreleased. The versioning scheme has not been decided; the Maven version is `0.1.0-SNAPSHOT`. The entries come from the Git history and [docs/implementation-plan.md](docs/implementation-plan.md); increments are named INC-xx, with the date they were recorded.

## [Unreleased]

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
- `Dockerfile` and `.dockerignore`: multi-stage image of the Spring Boot application (non-root user).
- `compose.yaml`: PostgreSQL 16 and the application, with healthchecks, a named data volume and an on-demand, idempotent `seed` service.
- `.github/workflows/ci.yml`: `test` (`mvn verify` on a PostgreSQL service), `build` (`mvn -B -DskipTests package`), `docker` (image build, not published) and `e2e` (Compose stack, seed, Playwright); `docker` and `e2e` need both `test` and `build`.
- README sections "Run with Docker", "Continuous integration" and "End-to-end test"; decision-log entries of 2026-11-18 for Docker, Compose and CI and for the hosting choice (TD-02, decided in part, not yet deployed).

### Changed
- INC-11 (2026-11-18): the interface shows task statuses in English (To do, In progress, Done), equal to the API values, instead of Portuguese labels; the decision-log entry of 2026-11-18 supersedes that of 2026-11-12.
- A task's owner must be an active member of the task's project, on create and on edit; otherwise 422 on `owner_member_id` (review finding S-07, decision-log entry of 2026-11-18).
- The `createTask` `Location` header is the project-scoped path `/projects/{project_id}/tasks/{task_id}`, as in `openapi.yaml`.
- Page and size parsing of the two list endpoints is shared in `shared/PageParams` (release review, no behaviour change).
- `Dockerfile`: both base images (`maven:3.9.11-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) are pinned by tag and sha256 digest.
- `.dockerignore`: keeps secrets and local files out of the build context; since the hardening below it is an allow-list (the Flyway `.sql` migrations are kept).
- `compose.yaml`: all three services (`db`, `app`, `seed`) use an explicit, dedicated network `matriz-net` instead of the implicit default network; the pinned `postgres` image has a comment. Compose was run end to end on 2026-11-18 (see `docs/verification.md`, "Docker Compose").
- `.github/workflows/ci.yml`: a `build` job was added, `compose.yaml` is validated, and actions are pinned by commit SHA.

### Removed
- INC-08 (2026-11-17): the unscoped route `PATCH /tasks/{task_id}` (`updateTaskStatus`) and its schema `TaskStatusUpdateRequest`; a task's status changes through the project-scoped `updateTask`. The regression test `TaskAccessTest.unscopedStatusRouteIsGone` was added.

### Fixed
- B-01 (2026-11-16): a success notice from an earlier action stayed visible above the task form when a later save failed.
- Review S-01 (2026-11-18): list pages whose offset overflows are refused with 422.
- Review S-05 (2026-11-18): an internal failure during sign-in is no longer reported as wrong credentials (401 `invalid_credentials`).

### Security
- Container hardening after the security audit: root-owned application jar, `no-new-privileges` and `cap_drop: [ALL]` for `app` and `seed` in `compose.yaml`, `postgres:16-bookworm` pinned by digest in `compose.yaml` and CI, and an allow-list `.dockerignore`.
- The README `curl` example keeps the session cookie jar outside the repository and no longer puts the password on the command line (audit finding S-04, 2026-11-17).
