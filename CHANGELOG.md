# Changelog

## Unreleased

Planned version for the next release: **0.2.0** (a new feature that does not break the API: Semantic Versioning, minor bump). `pom.xml` still says `0.1.0-SNAPSHOT`; changing it waits for your decision (the Dockerfile and CI refer to the jar name).

### Added
- Public sign-up (INC-12; FR-01, US-22): `POST /auth/sign-up` creates a personal account and signs it in; the sign-up and welcome screens; migration V5 (unique index on `lower(email)`); rate limits with 429 and `Retry-After` on sign-up and sign-in (NFR-11); a 16 KB body limit on `/auth/` routes (413); `MATRIZ_RATE_*` variables; `openapi.yaml` 0.9.0-draft; decision-log entries of 2026-11-18 and 2026-11-19 (SP-08 to SP-10, PD-25, PD-26, TD-13, BR-24).
- `Dockerfile` and `.dockerignore`: multi-stage image of the Spring Boot application (non-root user).
- `compose.yaml`: PostgreSQL 16 and the application, with healthchecks, a named data volume and an on-demand, idempotent `seed` service.
- `.github/workflows/ci.yml`: tests on a PostgreSQL service, Docker image build and Playwright end-to-end test.
- README sections "Run with Docker" and "Continuous integration"; decision-log entry of 2026-11-18.

### Changed
- Names refuse control and invisible formatting characters, emails are printable ASCII only (at most 254 characters), and `spring.mvc.formcontent.filter.enabled=false` (INC-12, security audits).
- Sign-in matches the email ignoring letter case and surrounding spaces, refuses passwords over 72 bytes with the generic 401, and counts failed attempts before the password is checked (INC-12).
- `server.forward-headers-strategy=none` is set explicitly, so forwarded headers are not trusted until TD-13 is decided (INC-12, security audit S-10).
- The interface uses the visual design approved on 2026-11-19 (docs/ui-design.md): colour tokens, Inter and Space Grotesk (self-hosted in `static/css/fonts`, SIL Open Font License), logo, status labels and a progress bar next to "N of M tasks done" (INC-13).
- `Dockerfile`: both base images (`maven:3.9.11-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) are pinned by tag and sha256 digest.
- `.dockerignore`: keeps secrets and local files out of the build context; since the hardening below it is an allow-list (the Flyway `.sql` migrations are kept).
- Container hardening after the security audit: root-owned application jar, `no-new-privileges` and `cap_drop: [ALL]` for `app` and `seed` in `compose.yaml`, `postgres:16-bookworm` pinned by digest in `compose.yaml` and CI, and an allow-list `.dockerignore`.
- `compose.yaml`: all three services (`db`, `app`, `seed`) use an explicit, dedicated network `matriz-net` instead of the implicit default network; the pinned `postgres` image has a comment. Compose was run end to end on 2026-11-18 (see `docs/verification.md`, "Docker Compose").
- The interface shows task statuses in English (To do, In progress, Done), equal to the API values, instead of Portuguese labels; decision-log entry of 2026-11-18 supersedes that of 2026-11-12 (INC-11).
