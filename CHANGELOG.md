# Changelog

## Unreleased

### Added
- `Dockerfile` and `.dockerignore`: multi-stage image of the Spring Boot application (non-root user).
- `compose.yaml`: PostgreSQL 16 and the application, with healthchecks, a named data volume and an on-demand, idempotent `seed` service.
- `.github/workflows/ci.yml`: tests on a PostgreSQL service, Docker image build and Playwright end-to-end test.
- README sections "Run with Docker" and "Continuous integration"; decision-log entry of 2026-11-18.

### Changed
- `Dockerfile`: both base images (`maven:3.9.11-eclipse-temurin-21`, `eclipse-temurin:21-jre-jammy`) are pinned by tag and sha256 digest.
- `.dockerignore`: extended to keep secrets, `.git`, logs, tests, `e2e/`, documentation and IDE files out of the build context (the Flyway `.sql` migrations are kept).
