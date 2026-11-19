# Changelog

## Unreleased

### Added
- `Dockerfile` and `.dockerignore`: multi-stage image of the Spring Boot application (non-root user).
- `compose.yaml`: PostgreSQL 16 and the application, with healthchecks, a named data volume and an on-demand, idempotent `seed` service.
- `.github/workflows/ci.yml`: tests on a PostgreSQL service, Docker image build and Playwright end-to-end test.
- README sections "Run with Docker" and "Continuous integration"; decision-log entry of 2026-11-18.
- `createWorkspace` (`POST /workspaces`, FR-02, US-02; INC-12, `openapi.yaml` 0.9.0-draft, now 0.10.0-draft with INC-13): any signed-in account can create a workspace and becomes its first member, a Workspace Administrator, in the same transaction; the name is trimmed, 3 to 100 characters, with no NUL character (refused with 422), duplicates allowed. The interface gets a "New workspace" link and form. 39 automated tests added (`WorkspaceApiTest` 19, `WorkspaceNameRuleTest` 19, `WorkspaceCreateRollbackTest` 1); decision-log entry of 2026-11-19 (PD-16 in part). INC-12 stays In progress.
- Workspace Administrators can create, edit and delete the projects of their workspace, like a Project Manager (INC-13, `openapi.yaml` 0.10.0-draft; PD-03 decided in part, decision log 2026-11-19). `AccessPolicy.checkManageProject` and the interface ("+ New project" and the create and edit forms) changed; task, assignee and comment rules for Workspace Administrators are unchanged. `ProjectApiTest` renamed `onlyProjectManagersCreateEditAndDelete` to `teamMemberAndViewerCannotCreateEditAndDelete` and gained Workspace Administrator tests; `WorkspaceCreateRollbackTest` added. The suite is 388 tests. INC-13 stays In progress.

### Changed
- `Dockerfile`: both base images (`maven:3.9.11-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) are pinned by tag and sha256 digest.
- `.dockerignore`: keeps secrets and local files out of the build context; since the hardening below it is an allow-list (the Flyway `.sql` migrations are kept).
- Container hardening after the security audit: root-owned application jar, `no-new-privileges` and `cap_drop: [ALL]` for `app` and `seed` in `compose.yaml`, `postgres:16-bookworm` pinned by digest in `compose.yaml` and CI, and an allow-list `.dockerignore`.
- `compose.yaml`: all three services (`db`, `app`, `seed`) use an explicit, dedicated network `matriz-net` instead of the implicit default network; the pinned `postgres` image has a comment. Compose was run end to end on 2026-11-18 (see `docs/verification.md`, "Docker Compose").
- The interface shows task statuses in English (To do, In progress, Done), equal to the API values, instead of Portuguese labels; decision-log entry of 2026-11-18 supersedes that of 2026-11-12 (INC-11).
