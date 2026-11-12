# Matriz — Implementation Plan

> **Status:** draft, created on 2026-11-12. It currently holds only the project setup increment; later increments are added when they are planned. Requirement IDs are defined in [requirements.md](requirements.md), user stories in [user-stories.md](user-stories.md), the stack in [architecture.md](architecture.md#technology-stack) and the data model in [database.md](database.md).

## Increments

| ID | Increment | Status |
|---|---|---|
| INC-01 | Project setup | Done (2026-11-12) |

### INC-01: Project setup

**Goal:** a project skeleton that starts, connects to PostgreSQL, creates the schema reproducibly and loads demo data, with no feature logic (no sign-in, endpoints or UI).

**Basis:** [Technology stack](architecture.md#technology-stack), decision rules 1–4 and CMP-01 to CMP-13 in [architecture.md](architecture.md); all entities and BR-01 to BR-20 in [database.md](database.md); NFR-01, NFR-03; SP-01, SP-06; AGENTS.md C2.

| ID | Acceptance criterion | Verification method | Result |
|---|---|---|---|
| AC-INC01.1 | The project has one package per module (CMP-01 to CMP-13) and uses only the documented stack. | Review of `pom.xml` and `src/main/java/com/matriz` | Pass |
| AC-INC01.2 | Configuration is read from environment variables; `.env.example` holds fictitious values only, and `.env` is ignored by Git. | `git check-ignore -v .env .env.example`; review of `.env.example` | Pass |
| AC-INC01.3 | Migrations create all 11 entities of database.md on an empty database, with no manual steps. | `mvn spring-boot:run` on an empty database: `Successfully applied 1 migration`; `DatabaseSetupTest` | Pass |
| AC-INC01.4 | The schema enforces the keys, uniqueness, required fields, enum values and delete behaviour of database.md. | SQL inserts, updates and deletes inside rolled-back transactions | Pass |
| AC-INC01.5 | The seed command loads demo data with at least two users and projects for different access levels; running it again inserts nothing and deletes nothing. | Seed command run twice; row counts compared | Pass |
| AC-INC01.6 | Demo passwords are stored as BCrypt hashes, never in plain text. | Query of `account.password_hash` prefix and length | Pass |
| AC-INC01.7 | Restarting the application keeps existing data. | Row counts before and after a restart | Pass |
| AC-INC01.8 | A database access check succeeds. | `mvn verify` (`DatabaseSetupTest`, 3 tests) | Pass |

**Deviations from the documentation, approved by the project decision-maker on 2026-11-12:**

- `account.password_hash` is added for demo passwords (BCrypt), although DA-08 says no credential data is modelled until TD-03 is decided. Updating database.md and TD-03 is proposed separately.
- Representation choices that the documentation leaves open: UUID identifiers (API-A4 leaves the format open), text columns without maximum lengths (PD-04), enums stored as upper-case codes with CHECK constraints.

**Not enforced by the schema** (left to the feature modules): BR-04, BR-09, BR-13 and BR-18, because they span several rows.

## Status log

| Date | Increment | Change | Approved by |
|---|---|---|---|
| 2026-11-12 | INC-01 | Plan approved; project skeleton, migration `V1__initial_schema.sql`, seed command and `DatabaseSetupTest` created; all acceptance criteria verified. Status set to Done. | Project decision-maker |
