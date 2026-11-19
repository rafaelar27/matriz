# Changelog

## Unreleased

### Added
- `Dockerfile` and `.dockerignore`: multi-stage image of the Spring Boot application (non-root user).
- `compose.yaml`: PostgreSQL 16 and the application, with healthchecks, a named data volume and an on-demand, idempotent `seed` service.
- `.github/workflows/ci.yml`: tests on a PostgreSQL service, Docker image build and Playwright end-to-end test.
- README sections "Run with Docker" and "Continuous integration"; decision-log entry of 2026-11-18.

### Changed
- The interface uses the visual design approved on 2026-11-19 (docs/ui-design.md): colour tokens, Inter and Space Grotesk (self-hosted in `static/css/fonts`, SIL Open Font License), logo, status labels and a progress bar next to "N of M tasks done" (INC-12).
- `Dockerfile`: both base images (`maven:3.9.11-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) are pinned by tag and sha256 digest.
- `.dockerignore`: keeps secrets and local files out of the build context; since the hardening below it is an allow-list (the Flyway `.sql` migrations are kept).
- Container hardening after the security audit: root-owned application jar, `no-new-privileges` and `cap_drop: [ALL]` for `app` and `seed` in `compose.yaml`, `postgres:16-bookworm` pinned by digest in `compose.yaml` and CI, and an allow-list `.dockerignore`.
- `compose.yaml`: all three services (`db`, `app`, `seed`) use an explicit, dedicated network `matriz-net` instead of the implicit default network; the pinned `postgres` image has a comment. Compose was run end to end on 2026-11-18 (see `docs/verification.md`, "Docker Compose").
- The interface shows task statuses in English (To do, In progress, Done), equal to the API values, instead of Portuguese labels; decision-log entry of 2026-11-18 supersedes that of 2026-11-12 (INC-11).
