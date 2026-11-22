# Matriz 0.3.0 - Release notes

| Item | Value |
|---|---|
| Version | 0.3.0 (Semantic Versioning, initial development phase 0.y.z; decision-log entry to be recorded on 2026-11-22) |
| Date | 2026-11-22 |
| Git tag | `v0.3.0` (annotated; created on the merge commit of the release pull request after the merge; find its commit with `git rev-list -n 1 v0.3.0`) |
| API contract | [openapi.yaml](../openapi.yaml) 0.13.0-draft (its version is independent of the application version) |
| Status | Candidate. No production release is published. A deployment on Render and Neon is live at https://matriz-761f.onrender.com/ (see Verification and Known limitations). |

This version follows 0.2.0 (`v0.2.0`, unchanged). The full list of changes is in [CHANGELOG.md](../CHANGELOG.md).

## What is included

| Increment | What it delivers | Main IDs | Status |
|---|---|---|---|
| INC-17 | Invitations by email (SMTP, optional) | FR-03, FR-04; US-03 | Done (per PR #26; acceptance criteria not re-checked here) |
| INC-18 | Edit a workspace: rename, change roles, remove members | FR-23, FR-04, FR-03; US-04, US-30, US-31 | Done (per PR #26) |
| INC-19 | Delete a workspace (soft) | FR-24; US-32 | Done (per PR #26) |

The PR #26 commit message says most documentation updates were left for a follow-up, and [implementation-plan.md](implementation-plan.md) does not yet list INC-17 to INC-19 [TO CONFIRM].

## Build and run

- Local build and run: [README, Getting started](../README.md#getting-started).
- Containers: [README, Run with Docker](../README.md#run-with-docker).
- Continuous integration: [README, Continuous integration](../README.md#continuous-integration).
- Hosted deployment (Render and Neon): [deployment.md](deployment.md).

## Verification

| Check | Result |
|---|---|
| `mvn verify` on `matriz_test` | 708 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS (local, 2026-11-22; to be re-run on 0.3.0 before the tag) |
| Docker Compose | `docker compose config -q`, build and start passed; `GET /` returned 200; the seed inserted 37 rows, then 0; no errors or warnings in the last 50 log lines (2026-11-22) |
| Public instance | `/` and `/config.js` returned 200 over HTTPS (2026-11-22); sign-up, sign-in, sign-out, workspace creation and the Members screen were reported working by the programmer |
| CI run | The GitHub Actions run of the release pull request (jobs `test`, `build`, `docker`, `e2e`); see the pull request's Checks tab. |
| Commit of the tag | The merge commit of the release pull request; `git rev-list -n 1 v0.3.0` prints it. |

Earlier verification evidence is in [verification.md](verification.md), [test-plan.md](test-plan.md), [review.md](review.md) and [delivery-report.md](delivery-report.md).

## Known limitations

No production release is published. Everything below is taken from the linked source.

- **Requirements not built** ([README, Features planned for the MVP](../README.md#features-planned-for-the-mvp)): FR-06 archiving projects (US-07), FR-07 choosing project members (US-08), FR-14, FR-17, FR-19, FR-20 and FR-21. Whether FR-03 and FR-04 are fully covered by INC-17 to INC-19 is not confirmed against every acceptance criterion [TO CONFIRM].
- **Not verified:** migrations V6 to V8 on Neon, SMTP email sending, and the exact commit deployed on Render.
- **Hosting and operations:** TD-05, TD-06 and TD-13 are pending ([constraints.md](constraints.md#pending-technical-decisions)). The hosted database is PostgreSQL 18 on Neon, while the tests, CI and Docker Compose use PostgreSQL 16 ([deployment.md](deployment.md)).
- **Specifications:** the requirement documents are still drafts pending client validation.
- **Provisional rules** carried over from 0.2.0 ([constraints.md](constraints.md#pending-product-decisions)): PD-01, PD-03 in part, PD-17, PD-20, PD-25 and PD-26.
