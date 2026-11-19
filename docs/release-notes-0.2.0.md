# Matriz 0.2.0 - Release notes

| Item | Value |
|---|---|
| Version | 0.2.0 (Semantic Versioning, initial development phase 0.y.z; decision log 2026-11-19) |
| Date | 2026-11-19 |
| Git tag | `v0.2.0` (annotated; created on the merge commit of the release pull request after the merge; find its commit with `git rev-list -n 1 v0.2.0`) |
| API contract | [openapi.yaml](../openapi.yaml) 0.12.0-draft (its version is independent of the application version) |
| Status | Candidate. No production release is published. A first deployment is reported ([AGENTS.md](../AGENTS.md), [implementation-plan.md](implementation-plan.md)) but has not been validated; no public check is recorded (see Known limitations). |

This is the first tagged version. It follows the never-released `0.1.0-SNAPSHOT`. The full list of changes is in [CHANGELOG.md](../CHANGELOG.md).

## What is included

Requirement and user-story IDs are those of each increment's "Basis" in [implementation-plan.md](implementation-plan.md). Statuses are those of its increment table.

| Increment | What it delivers | Main IDs | Status |
|---|---|---|---|
| INC-01 | Project skeleton, PostgreSQL schema through Flyway, demo-data seed command | NFR-01, NFR-03 | Done |
| INC-02 | Sign-in, profile, sign-out with a server-side session and CSRF protection; one access policy (CMP-03) | FR-01, FR-04, FR-05; US-01 | Done |
| INC-03 | Projects: create, list with search and pages, view, edit, delete; first user interface | FR-06, FR-08, FR-18; US-06, US-09, US-24, US-27, US-28 | Done |
| INC-04 | Tasks within a project: create, list, filter by status and owner, view, edit, delete, change status | FR-09 to FR-13, FR-15 (by status and owner only), FR-18; US-10, US-11, US-12, US-14, US-25, US-26 | Done |
| INC-05 | Task assignees and comments | FR-10, FR-16, FR-22; US-15, US-29 | Done |
| INC-06 | User interface reworked one screen at a time (sign-in, dashboard, project list and project form so far) | FR-01, FR-06, FR-08, FR-13, FR-18; US-01, US-06, US-09, US-17, US-19, US-24, US-28; SP-01; NFR-01, NFR-06, NFR-07 | In progress (partly included) |
| INC-07 | Project owner required; start date not after the due date | FR-06 | Done |
| INC-08 | Removal of the unscoped task status route `PATCH /tasks/{task_id}`; a status is changed through `updateTask` | FR-12; US-11 | Done |
| INC-09, INC-10 | Coverage measurement (JaCoCo) and unit tests | FR-04, FR-05, FR-06, FR-09, FR-10, FR-12, FR-16, FR-22; NFR-01 | Done |
| INC-11 | Task statuses shown in English (To do, In progress, Done) | FR-12 | Done |
| INC-12 | Public sign-up with rate limits | FR-01, NFR-11; US-22 | Done, except validation of the hosted deployment (a first deployment is reported but not validated; no public check is recorded) |
| INC-13 | Approved UI visual design applied to the existing screens | FR-08, FR-12, FR-13, FR-16, FR-18; NFR-06 to NFR-08 | Done (some manual checks still open) |
| INC-14 | Create a workspace | FR-02; US-02 | Done |
| INC-15 | Workspace Administrators create, edit and delete projects | FR-06; US-06, US-24, US-27 | Done |
| INC-16 | Workspace Administrators work on tasks in a limited way | FR-04, FR-09, FR-10, FR-12, FR-16, FR-22 | Done |

Also in this version, outside the numbered increments of the table: Docker, Docker Compose and GitHub Actions (decision log 2026-11-18), the Workspaces screen (E-22, FR-02, FR-03, US-02), the connection to a Neon database for local development (PR #19), the Render and Neon deployment guide with `render.yaml` (PR #20) and a test-isolation fix, `AuthControllerSignInTest` clearing `SecurityContextHolder` (PR #23, test only) (CHANGELOG).

## Build and run

- Local build and run: [README, Getting started](../README.md#getting-started).
- Containers: [README, Run with Docker](../README.md#run-with-docker).
- Continuous integration: [README, Continuous integration](../README.md#continuous-integration).
- Hosted deployment (Render and Neon): [deployment.md](deployment.md).

## Verification

| Check | Result |
|---|---|
| `mvn verify` on `matriz_test` | 535 tests, 0 failures, 0 errors, BUILD SUCCESS (PostgreSQL 16, local, 2026-11-19) |
| CI run | The GitHub Actions run of the release pull request (jobs `test`, `build`, `docker`, `e2e`); see the pull request's Checks tab. A commit cannot name the run that checks it. |
| Commit of the tag | The merge commit of the release pull request; `git rev-list -n 1 v0.2.0` prints it. |

Earlier verification evidence is in [verification.md](verification.md), [test-plan.md](test-plan.md) and [review.md](review.md).

## Known limitations

No production release is published. Everything below is taken from the linked source.

**Requirements not built** ([README, Features planned for the MVP](../README.md#features-planned-for-the-mvp); [implementation-plan.md, Increments](implementation-plan.md#increments)):

- FR-03 invitations and member management, and FR-04 role assignment and changes (only reading a workspace's active members is built; the FR-04 permissions are enforced).
- FR-06 archiving projects (US-07) and FR-07 choosing project members (US-08), apart from the creator becoming a member.
- FR-14 "My tasks", FR-17 in-app notifications, FR-19 overdue highlighting, FR-20 feedback, FR-21 personal-data requests.
- Left out by decision: task priority (F17, [decision log](constraints.md#decision-log) 2026-11-12) and filtering tasks by due date (FR-15).
- INC-06 (user interface, one screen at a time) is still in progress.

**Provisional rules** (pending product decisions, [constraints.md](constraints.md#pending-product-decisions)): PD-01 (Team Members' task rights), PD-03 except the parts decided on 2026-11-19, PD-17 (Viewers who own a task), PD-20 (project membership needed to create a task). PD-25 (email verification) and PD-26 (password reset) are not built.

**Hosting and operations** ([constraints.md, decision log 2026-11-19](constraints.md#decision-log); [pending technical decisions](constraints.md#pending-technical-decisions); [deployment.md](deployment.md)): TD-02 was decided on 2026-11-19: the application on one Render Docker Web Service (free plan, Frankfurt) and the database on Neon (PostgreSQL 18, AWS Europe Central 1 / Frankfurt, `eu-central-1`, `production` branch). A first deployment is reported ([AGENTS.md](../AGENTS.md), [implementation-plan.md](implementation-plan.md)) but has not been validated; no public check is recorded. Backup (TD-05), availability (TD-06) and how the client IP is read behind a proxy (TD-13) are pending. Sessions and sign-up and sign-in rate counters are kept in memory and are lost when the application restarts (TD-03, NFR-11; [deployment.md](deployment.md)).

**Database version:** the hosted database is PostgreSQL 18 on Neon, while the automated tests, CI and Docker Compose use PostgreSQL 16 (`postgres:16-bookworm`); the decision log marks the behaviour on Neon as [TO CONFIRM] ([deployment.md](deployment.md)).

**Specifications:** the requirement documents are still drafts pending client validation ([requirements.md](requirements.md), [README, Current project status](../README.md#current-project-status)). Open sign-up (decision log 2026-11-18) is in tension with AQ3, which stays unchanged.

**Checks not done** (accepted remainders, not failures):

- INC-13: several manual browser checks are open (focus ring, keyboard use, contrast and others; [implementation-plan.md, INC-13](implementation-plan.md#inc-13-apply-the-approved-ui-visual-design)).
- INC-14 to INC-16: the browser checks were a scripted run, not manual, and the live checks of the three security audits were not run (the audits were static); see the status log of [implementation-plan.md](implementation-plan.md#status-log), 2026-11-19.
- INC-06: acceptance criteria listed under "Not verified yet" in [implementation-plan.md, INC-06](implementation-plan.md#inc-06-user-interface).
- [review.md, Verifications still pending](review.md#verifications-still-pending) and the "Not verified" sections of [test-plan.md](test-plan.md).
- Everything about Render and Neon behaviour is marked [TO CONFIRM] and not verified in [deployment.md](deployment.md); no public check of the reported first deployment is recorded.
