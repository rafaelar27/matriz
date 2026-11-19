# Matriz — Deployment notes (Docker Compose, Render and Neon)

> **Status (2026-11-19):** hosting was **decided** on 2026-11-19 by the project decision-maker (KC-01; see the [decision log](constraints.md#decision-log)): one Docker Web Service on **Render** (free plan, region Frankfurt) for the application and **Neon** (managed **PostgreSQL 18**, AWS Europe Central 1 / Frankfurt, `eu-central-1`) for the database, using the `production` branch (the default branch) of the existing Neon project. Docker Compose and CI use PostgreSQL 16 (`postgres:16-bookworm`); the hosted database is 18. **A first deployment exists but has not been validated; no public check is recorded here, no public URL is recorded (live demo: recorded after validation), and no step in the Render and Neon sections is recorded here as run or verified against Render or Neon.** Still pending: backup (TD-05), availability (TD-06) and how the client address is read behind the proxy (TD-13) ([pending technical decisions](constraints.md#pending-technical-decisions)). The programmer deploys by hand and enters every secret in the Render dashboard. Requirement IDs are defined in [requirements.md](requirements.md); the sign-up rules are in [user-stories.md](user-stories.md#acceptance-criteria) (US-22) and the decision log.
>
> **Verification status:** facts about the code and the repository files (`compose.yaml`, `Dockerfile`, `render.yaml`, `.env.example`, `application.properties`, `.github/workflows/ci.yml`) were read from those files. Documentation of Render and Neon was **not reachable** when this file was written, so every statement about how Render or Neon behaves (free-plan limits, cold start, default port, region names, Blueprint field names, Neon storage, compute and restore limits, connection endpoints, TLS) is marked **[TO CONFIRM]** and must be checked against the providers' current documentation before the first deployment.

## Contents

- [Decisions this file relies on](#decisions-this-file-relies-on)
- [Docker Compose (local)](#docker-compose-local)
- [Before the first deployment](#before-the-first-deployment)
- [Neon setup](#neon-setup)
  - [Local development with Neon](#local-development-with-neon)
- [Render setup](#render-setup)
- [Environment variables (set only in the Render dashboard)](#environment-variables-set-only-in-the-render-dashboard)
- [The render.yaml file](#the-renderyaml-file)
- [Database migration step](#database-migration-step)
- [Redeploy, logs and rotating secrets](#redeploy-logs-and-rotating-secrets)
- [Free-tier limitations](#free-tier-limitations)
- [Rate limiting and the client address (TD-13, NFR-11)](#rate-limiting-and-the-client-address-td-13-nfr-11)
- [Free-tier abuse risk](#free-tier-abuse-risk)
- [Checks after the first deployment (to run, with approval)](#checks-after-the-first-deployment-to-run-with-approval)
- [Troubleshooting](#troubleshooting)
- [Not decided or not verified](#not-decided-or-not-verified)

## Decisions this file relies on

| Decision | Source |
|---|---|
| Render (one Docker Web Service, free plan) for the application and Neon (PostgreSQL 18, direct endpoint, TLS) for the database. Regions: Render Frankfurt (EU Central; exact Render region name [TO CONFIRM]); Neon AWS Europe Central 1 (Frankfurt, `eu-central-1`). The public site uses the `production` branch (default branch) of the existing Neon project. Compose and CI use PostgreSQL 16, Neon uses 18. Compatibility with PostgreSQL 18 on Neon: the migrations use only core SQL and `gen_random_uuid()`, and local verification used 18.4 ([verification.md](verification.md#environment-and-setup)); behaviour on Neon is [TO CONFIRM] until the first deployment | TD-02, decided 2026-11-19 ([decision log](constraints.md#decision-log)) |
| One service serves the API and the static user interface from the same origin. A separate Render Static Site is **not** used: `onrender.com` subdomains are different sites [TO CONFIRM], and the session and CSRF cookies are same-site only (TD-03). The `MATRIZ_API_BASE_URL` rule is unchanged (empty, or a path on the same site) | Decision log 2026-11-19 |
| No CORS configuration exists in the code (no CORS setup in `SecurityConfig`), so a browser request from another origin is refused by the browser | Code: `src/main/java/com/matriz/accesscontrol/SecurityConfig.java` |
| No demo seed on the public database; accounts come from public sign-up (INC-12) | Decision log 2026-11-18 and 2026-11-19 |
| Migrations run through Flyway when the application starts (V1 to V5), are non-destructive and reproducible; the application never wipes the database (`spring.flyway.clean-disabled=true`) | `application.properties`; `src/main/resources/db/migration` |
| Backup (TD-05), availability (TD-06) and client-address handling (TD-13) stay pending | [constraints.md](constraints.md#pending-technical-decisions) |
| Release candidate version: `0.2.0` (Semantic Versioning, [decision log](constraints.md#decision-log) 2026-11-19), tagged `v0.2.0` | Decided |

## Docker Compose (local)

`compose.yaml` runs the whole application on one machine for local use and for the CI end-to-end job. It is **not** used on Render. The runtime view is in the [architecture, deployment view](architecture.md#deployment-view).

| Item | Value (from `compose.yaml`) |
|---|---|
| Services | `db`, `app`, `seed` |
| `db` | Image `postgres:16-bookworm` pinned by digest; database `matriz`; role and password from `MATRIZ_DB_USER` and `MATRIZ_DB_PASSWORD` (both required, Compose stops with a message if one is unset); no published port; `restart: unless-stopped` |
| `app` | Built from the `Dockerfile` (image tag `matriz:local`); starts after `db` is healthy; connects to `jdbc:postgresql://db:5432/matriz` (this overrides `MATRIZ_DB_URL` from `.env`); listens on 8080 inside the container; `no-new-privileges` and all capabilities dropped; `restart: unless-stopped` |
| `seed` | Same image; profile `seed`, so `docker compose up` does not start it; runs with `SPRING_PROFILES_ACTIVE=seed`, loads the demo data, then exits (`restart: "no"`); needs `MATRIZ_DEMO_PASSWORD` |
| Network | `matriz-net` (services reach each other by service name) |
| Volume | `matriz-db`, mounted at `/var/lib/postgresql/data` of `db` |
| Published port | `127.0.0.1:${MATRIZ_PORT:-8080}:8080`: reachable from this computer only. The stack uses plain HTTP and, by default, a non-Secure session cookie (TD-02, TD-03); do not expose it on a network |
| Healthchecks | `db`: `pg_isready` every 5 s (20 retries). `app`: `curl -fsS http://localhost:8080/config.js` every 10 s, timeout 5 s, 12 retries, 30 s start period (`/config.js` needs no session) |

**Variables read from `.env` by Compose:** `MATRIZ_DB_USER`, `MATRIZ_DB_PASSWORD` (required), `MATRIZ_PORT` (host port, default 8080), `MATRIZ_SESSION_TIMEOUT` (default `8h`), `MATRIZ_COOKIE_SECURE` (default `false`), `MATRIZ_API_BASE_URL` (default empty) and, for `seed` only, `MATRIZ_DEMO_PASSWORD`. The rate-limit variables and `SERVER_FORWARD_HEADERS_STRATEGY` are **not** passed by `compose.yaml`, so Compose always uses their defaults.

**Common commands** (from the repository root; they are the commands documented in `compose.yaml` and the README, [Run with Docker](../README.md#run-with-docker)):

| Command | Effect |
|---|---|
| `docker compose up -d --build --wait` | Builds the image and starts `db` and `app`; returns when both are healthy |
| `docker compose run --rm seed` | Loads the demo data; idempotent, never deletes |
| `docker compose logs --no-color app` | Shows the application log (Flyway messages are at the start) |
| `docker compose ps` | Shows the state and health of the services |
| `docker compose down` | Stops and removes the containers; **keeps** the data volume |
| `docker compose down -v` | Stops and removes the containers **and deletes the database volume `matriz-db`. This cannot be undone** (C1: ask for approval first) |

**Data reset warning:** `docker compose down -v` deletes every account, project and task in the local Compose database and cannot be undone. Use it only on a database whose data you can lose.

**Troubleshooting (Compose):**

| Symptom | Likely cause and action |
|---|---|
| `set MATRIZ_DB_USER in .env` or `set MATRIZ_DB_PASSWORD in .env` | A required variable is missing: copy `.env.example` to `.env` and set it |
| The application cannot sign in to the database after you changed `MATRIZ_DB_PASSWORD` | The role in the existing volume keeps its first password. Reset with `docker compose down -v` (deletes the data) or change the role password inside PostgreSQL |
| `docker compose run --rm seed` stops with `MATRIZ_DEMO_PASSWORD is not set` | Set `MATRIZ_DEMO_PASSWORD` in `.env` |
| `app` stays unhealthy | Read `docker compose logs --no-color app`; the healthcheck waits up to the start period plus 12 retries |
| Port already in use | Another program uses the host port: set `MATRIZ_PORT` in `.env` |
| First build fails with `429 Too Many Requests` or `PKIX path building failed` | Docker Hub pull limit, or a proxy that re-signs certificates (see the README, [Run with Docker](../README.md#run-with-docker)) |

The plain local variant without Docker (`mvn spring-boot:run` with a local PostgreSQL) is in the README, [Getting started](../README.md#getting-started).

## Before the first deployment

1. **Pending decisions:** TD-05 (backup), TD-06 (availability) and TD-13 (client address) stay pending. The programmer decides, with the decision-maker, whether to deploy before they are decided, and records each decision in the decision log (D2).
2. **Check the Neon database first** (read-only): migration V5 fails as a whole, and changes nothing, if two accounts have emails that differ only by letter case. On a database with no accounts there is nothing to check. PR #19 (merged on 2026-11-19) reports that Flyway already applied V1 to V5 to the `production` branch and that one test account was created and deleted there (reported in PR #19, not verified here), so run the check before the first deployment.
   ```sql
   SELECT lower(email), count(*) FROM account GROUP BY 1 HAVING count(*) > 1;
   ```
3. **Do not run the seed command on the public database** (decision log 2026-11-18 and 2026-11-19). If a public demo with demo accounts is wanted, it is a separate deployment with its own database.
4. Use Neon's **direct** (non-pooled) connection string for the application, because Flyway applies the migrations when the application starts. How Flyway behaves on Neon's pooled endpoint is not verified [TO CONFIRM]; the decision is the direct endpoint.
5. **Never run `mvn verify` against Neon.** The tests change data in the database they connect to and `DatabaseSetupTest` applies migrations there. `mvn verify` runs only against a separate local `matriz_test` database (README, [step 6](../README.md#6-run-the-verification-check)).
6. Keep every secret out of the repository, chat and logs (C2, C3). The Neon connection string and role password are entered only in the Render dashboard.

## Neon setup

Dashboard steps for the programmer. None has been run; all Neon behaviour is [TO CONFIRM] against Neon's current documentation.

1. Sign in to the Neon console and open the **existing** project (do not create a new one). Check that its region is **AWS Europe Central 1 (Frankfurt, `eu-central-1`)**, that its PostgreSQL version is **18**, and that the branch the public site uses is **`production`**, the default branch. Do **not** create a separate branch only for the public site. `neon.ts` (merged with PR #19) gives a new non-default branch, created with `neon checkout <name>`, a time to live of 7 days (`ttl: "7d"`); the default branch `production` and existing branches get no override. What Neon does when the time to live ends is [TO CONFIRM]. Exact console labels are [TO CONFIRM].
2. Use the existing database and role, or create them. Use a dedicated role for Matriz with a strong, unique password; note that the application needs to create and change tables, because Flyway runs as this role.
3. Copy the **direct** connection string with connection pooling **off** (the host without the `-pooler` suffix [TO CONFIRM]). Keep it private: it is a secret (C2).
4. The programmer builds the JDBC URL and sets it in the Render dashboard as `MATRIZ_DB_URL=jdbc:postgresql://<neon-direct-host>/<database>?sslmode=...`, with the direct (non-pooled) host (all values here are placeholders). The role name and the password go in the separate variables `MATRIZ_DB_USER` and `MATRIZ_DB_PASSWORD`, not inside the URL. The local mechanism described in [Local development with Neon](#local-development-with-neon) is inactive on Render, because `MATRIZ_DB_URL` is set explicitly there.
5. TLS: recommended `sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`, which verifies the server certificate against the JVM trust store. There is no fallback without verification: do not use `sslmode=require`, which with pgJDBC encrypts but accepts any server certificate, so a machine in the middle could pose as Neon and capture the database password and data. If the certificate chain is not accepted, fix the trust store instead (the start fails until then). This setting is [TO CONFIRM] against Neon (also server name indication) and has not been tested. The derived local connection has no `require` fallback: it always uses `sslmode=verify-full` with `DefaultJavaSSLFactory` (JVM trust store). If its certificate check fails, the local start fails. In that case set `MATRIZ_DB_URL` yourself, which switches the derivation off.
6. Check the free plan's storage, compute-hours, idle suspension and history (restore) window in Neon's documentation and write them in the decision log when known. They are [TO CONFIRM] and they decide TD-05.
7. Run the read-only SQL check from [Before the first deployment](#before-the-first-deployment) only if the database is not new.

### Local development with Neon

Local use only; nothing here applies to Render. The Neon CLI (`neon env pull` or `neon deploy`) writes `.env.local` in the repository root (Git-ignored: `.gitignore` has `.env.*`). The application imports it after `.env` (`spring.config.import`). `.env.local` holds `DATABASE_URL_UNPOOLED`, the direct (non-pooled) endpoint, which Flyway needs. When `MATRIZ_DB_URL` is set nowhere (shell, `.env`), `storage/NeonDatabaseEnvironment` derives `MATRIZ_DB_URL`, `MATRIZ_DB_USER` and `MATRIZ_DB_PASSWORD` from it, all three together, even if `MATRIZ_DB_USER` or `MATRIZ_DB_PASSWORD` are set elsewhere. The URL is always `jdbc:postgresql://<host>[:port]/<db>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`. The `sslmode` in the Neon URL is ignored, and only `channel_binding` is kept, as `channelBinding`. The variable name, the file name and what the CLI writes are taken from the code and its comments; the CLI's behaviour is [TO CONFIRM]. Automated tests never use this path: Maven's Surefire sets `matriz.neon.enabled=false`, so without `MATRIZ_DB_URL` pointing at the test database they stop at startup. The pgJDBC driver is 42.7.14 (override in `pom.xml`, advisory GHSA-j92g-9f8w-j867, which concerns channel binding).

## Render setup

Dashboard steps for the programmer. None has been run; all Render behaviour is [TO CONFIRM] against Render's current documentation.

1. Sign in to Render and connect the GitHub account to the repository.
2. Create the service in one of two ways:
   - **Blueprint:** New, Blueprint, choose the repository and the branch that holds `render.yaml` (see [The render.yaml file](#the-renderyaml-file)); or
   - **Manually:** New, Web Service, from the repository, runtime **Docker** (the `Dockerfile` at the repository root), instance type **Free**, region **Frankfurt** (exact name [TO CONFIRM]; the same area as Neon), branch as agreed (`render.yaml` names `master`).
3. Set the **health check path** to `/config.js`. It is open without a session and holds no secret, and it is the path used by the Compose healthcheck. A path that needs a session would answer 401 and the check would fail.
4. Set the environment variables listed in the [next section](#environment-variables-set-only-in-the-render-dashboard). Enter the three database secrets by hand.
5. Create the service. Render builds the image from the `Dockerfile` (Maven build stage, then a JRE runtime stage, non-root user) and starts it. The first build and start are slow [TO CONFIRM how slow on the free plan].
6. When the service is live, note the public address and add it to README, [Live demo](../README.md#live-demo) (a first deployment exists but has not been validated; no public check is recorded here), and to `openapi.yaml` only after approval.
7. Do not enable automatic deploys unless approved: `render.yaml` sets deploys to manual.

## Environment variables (set only in the Render dashboard)

Never commit values. `.env.example` holds fictitious placeholders only. The table lists names and purposes; values are not recorded here. "Secret" means the programmer enters the value in the dashboard and it is never written in a file, a log or a message.

| Variable | Purpose | Secret | Notes |
|---|---|---|---|
| `MATRIZ_DB_URL` | JDBC URL of the Neon database | Yes (treat the host and database name as private) | Direct (non-pooled) endpoint; `sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`, no fallback without certificate verification ([TO CONFIRM]). `render.yaml`: `sync: false` |
| `MATRIZ_DB_USER` | Database role | Yes | `sync: false` |
| `MATRIZ_DB_PASSWORD` | Database role password | **Yes** | `sync: false` |
| `PORT` | Port Render routes traffic to | No | `render.yaml` sets `10000` [TO CONFIRM: Render's default port] |
| `MATRIZ_PORT` | HTTP port of the application (`server.port`) | No | Must equal `PORT`; `render.yaml` sets `10000` |
| `MATRIZ_COOKIE_SECURE` | `true` | No | The public site is HTTPS, so the session cookie must be Secure |
| `MATRIZ_SESSION_TIMEOUT` | Idle timeout of a session | No | Default `8h`; `render.yaml` sets `8h` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | How forwarded headers are read (TD-13, pending) | No | `render.yaml` sets `native`, which overrides `server.forward-headers-strategy=none`. This is an approved trial (decision log 2026-11-19); TD-13 stays pending until the result is verified on the live service. See [Rate limiting](#rate-limiting-and-the-client-address-td-13-nfr-11). Changing it is a security setting (C1) |
| `JAVA_TOOL_OPTIONS` | JVM options | No | `render.yaml` sets `-XX:MaxRAMPercentage=75` [TO CONFIRM against the memory of the free instance] |
| `MATRIZ_RATE_SIGN_UP_MAX`, `MATRIZ_RATE_SIGN_UP_WINDOW` | Sign-up limit per client address | No | Optional; defaults 5 per `1h` (NFR-11); not in `render.yaml` |
| `MATRIZ_RATE_SIGN_IN_MAX_PER_IP_AND_EMAIL`, `MATRIZ_RATE_SIGN_IN_MAX_PER_IP`, `MATRIZ_RATE_SIGN_IN_WINDOW` | Failed sign-in limits | No | Optional; defaults 10, 50 and `15m`; not in `render.yaml` |

**Not set on the public service:** `MATRIZ_DEMO_PASSWORD` (no demo data on the public database) and `MATRIZ_API_BASE_URL` (empty means the same origin; any other value than a path on the same site stops the application at startup). `MATRIZ_TEST_DB_URL` is for local tests only.

A missing `MATRIZ_DB_URL`, `MATRIZ_DB_USER` or `MATRIZ_DB_PASSWORD` stops the application at startup (`application.properties` has no default for them).

## The render.yaml file

`render.yaml` at the repository root describes the service as a Render Blueprint. It holds **no secrets**: the three database variables use `sync: false`, so Render asks for their values in the dashboard. It sets: one `web` service named `matriz`, `runtime: docker`, `plan: free`, `region: frankfurt`, `branch: master`, `dockerfilePath: ./Dockerfile`, manual deploys, `healthCheckPath: /config.js`, and the non-secret variables of the table above.

**Its field names and values were written without access to Render's Blueprint reference and are [TO CONFIRM]** (the comments in the file say the same): the field names `runtime`, `plan`, `region`, `autoDeploy` (newer Blueprints may use `autoDeployTrigger`) and `healthCheckPath`; the region value `frankfurt`; the default port `10000`; and whether the service name `matriz` is free. Render validates the Blueprint when it is created; a validation error is the first signal of a wrong field name. The file has not been validated against Render.

## Database migration step

There is no separate migrate command. When the application starts on Render, Flyway applies the pending migrations from `src/main/resources/db/migration` to the Neon database, then the application listens.

- On an empty Neon database Flyway applies **V1 to V5** (five migrations: `V1__initial_schema`, `V2__project_delete_cascade`, `V3__task_assignees`, `V4__project_owner_required_and_date_order`, `V5__account_email_case_insensitive`). On a later start the log says the schema is up to date. The exact log wording on Render has not been seen.
- Migrations are versioned and non-destructive for data: the application never wipes the database (`spring.flyway.clean-disabled=true`), and the same migrations produce the same schema every time. V4 stops startup if a project has no owner or a start date after its due date, and V5 stops it if two emails differ only by letter case (see the check above); neither can happen on a new database.
- **Read the log:** in the Render dashboard, open the service, Logs [TO CONFIRM menu names]. Look for the Flyway lines at the start (applied migrations or "up to date") and for `Started MatrizApplication`. A failed migration stops the application, the service does not become healthy, and the log shows the error. A failed migration does not delete data.
- **No seed command** is run on the public database. **Never run `mvn verify` against Neon** (see [Before the first deployment](#before-the-first-deployment)).

## Redeploy, logs and rotating secrets

- **Redeploy:** deploys are manual (`autoDeploy: false` in `render.yaml` [TO CONFIRM field]). In the dashboard choose a manual deploy of the latest commit [TO CONFIRM menu names]. A deploy restarts the application, which ends every session and resets the rate-limit counters (they are in memory). Data in Neon is kept. New migrations are applied at that start.
- **Logs:** the Render dashboard shows the application's output. The application does not log passwords. Do not paste logs that contain connection strings or personal data into tickets or chats (C2).
- **Rotate the database password:** (1) change the role password in the Neon console; (2) enter the new password as `MATRIZ_DB_PASSWORD` in the Render dashboard; (3) redeploy. Between steps 1 and 3 the running service may lose its database connection. Every session ends at the redeploy; people sign in again. If the connection string changed, update `MATRIZ_DB_URL` too. The old password must not be written anywhere.
- **If a secret may have leaked:** rotate it at once as above and tell the project decision-maker (C3).

## Free-tier limitations

All items are **[TO CONFIRM]** against Render's and Neon's current documentation; none has been observed.

- **Render free web service** may be stopped when idle and started by the next request (a **cold start**: the first request can take a long time and the interface may seem not to answer) [TO CONFIRM]. Memory and CPU are small [TO CONFIRM]; BCrypt hashing is CPU-heavy.
- A stop or restart ends every session (sessions are in memory, TD-03) and resets the rate-limit counters. People see 401 `session_expired` and must sign in again.
- There is one instance. The rate limits and sessions are only correct with one instance.
- **Neon free plan** has limits on storage, compute and history [TO CONFIRM], and may suspend an idle database, which adds delay to the first request after a pause [TO CONFIRM]. Backup and recovery are pending (TD-05); whether a restore window exists is [TO CONFIRM].
- No service-level targets exist (TD-06; NFR-04 and NFR-05 targets are not met by any measurement).

## Rate limiting and the client address (TD-13, NFR-11)

- The limits are kept in the memory of the running application. They start again at every restart, and they are only correct with one instance. A free Render service sleeps when idle and restarts [TO CONFIRM], which also ends every session.
- `application.properties` sets `server.forward-headers-strategy=none` (explicit since 2026-11-19), so by default the application sees the address of the direct connection. **Behind Render's proxy, every visitor may therefore look like one address.** Then the sign-up limit (5 per hour) is shared by everyone, one person can use it up, and 50 failed sign-ins lock everyone out of sign-in for 15 minutes. This is a known risk until TD-13 is decided.
- **Approved trial, not a decision on TD-13:** the project decision-maker approved on 2026-11-19 (see the [decision log](constraints.md#decision-log)) setting `SERVER_FORWARD_HEADERS_STRATEGY=native` on the Render service to observe which client address the application sees. `render.yaml` sets it as an environment variable, which overrides the property without a code change. If Render's proxy is not trusted, every visitor still shares one address, as with `none`. **No result has been verified.** `native` makes the embedded server read `X-Forwarded-For` from trusted proxy addresses only. Which proxy addresses count as trusted by default, and whether Render's proxy is among them, are [TO CONFIRM]. A wrong setting either shares one address among all users or lets a client forge its address and avoid the limits. The behaviour must be **observed on the live service** (which client address the application sees: for example, check that two people on different networks do not share the sign-up limit) and recorded by the decision-maker. **TD-13 stays pending until then.** Two checks decide it: (1) the `Secure` flag of the `XSRF-TOKEN` cookie (check 3 below), which shows whether the application treats the request as HTTPS behind the proxy; (2) a request that shows which address the rate limit counts, for example two people on different networks signing up and checking that they do not share the limit (checks 11 and 12 below). The decision-maker records the result in the decision log. Changing the setting is a security setting and needs approval (C1).
- The counters are bounded in two tiers. Per-address keys (200,000): when full, a new address is refused with 429 until a window ends and existing lockouts are never forgotten; filling it takes 200,000 distinct addresses. Per-address-and-email sign-in keys (200,000, up to 50 per address): when full, a new key is not stored and only the per-address limit applies, so a flood cannot block everyone. Roughly 100 to 200 bytes per key, so a full table is tens of megabytes, which matters on a small free instance.
- Bodies over 16 KB on `/auth/` routes get 413, and form-encoded PUT, PATCH and DELETE bodies are not read (the form filter is disabled).

## Free-tier abuse risk

Anyone can sign up (FR-01). Mass sign-ups fill account rows in Neon's small free storage. A new account can create a workspace (FR-02, INC-14) and then projects, tasks and comments as its Workspace Administrator (INC-15, INC-16), so a spam account can also leave larger rows, and there is no tool to delete spam accounts: removing them is SQL on Neon, a destructive action that needs approval (C1). BCrypt is slow by design, so floods of sign-up and sign-in requests can use up the CPU of a small Render instance; the rate limits and the 16 KB body limit are the protection, and they reset at every restart. There is no email verification (PD-25), CAPTCHA or password reset (PD-26).

## Checks after the first deployment (to run, with approval)

**Scope.** A new account created by public sign-up has no workspace at first and sees the welcome screen (AC-22.8). It can create a workspace with **+ New workspace** beside the Dashboard heading (E-21; FR-02, INC-14) and becomes its Workspace Administrator, who can then create projects (INC-15) and tasks and comments (INC-16). The public checks may therefore include the full flow (sign-up, workspace, project, task, comment) and cross-account isolation with two accounts. These are **checks to run with approval**; each step creates real rows on the public database, and their results are not recorded here. Removing the test rows needs SQL on Neon, a destructive action that needs approval (C1).

Run only with fictitious addresses (for example `check1@example.com`, never a real person's), each step approved by the programmer first, because they create real rows on the public database. The address below is written `<public-url>`; no public URL is recorded here. **No result of any step is recorded here**, so the Result column is empty.

| # | Check | Expected | Result |
|---|---|---|---|
| 1 | Open `<public-url>` and `<public-url>/config.js` over HTTPS | HTTPS with a valid certificate; `/config.js` answers 200 without a session and holds no secret | not run |
| 2 | Sign up with a fictitious account | 201; the person is signed in; welcome screen with no workspace (AC-22.8) | not run |
| 3 | Look at the cookies after sign-up (browser developer tools) | `MATRIZ_SESSION` has `Secure`, `HttpOnly` and `SameSite=Lax`; `XSRF-TOKEN` is readable by the page and has `Secure` [TO CONFIRM: the CSRF cookie's Secure flag is not set by the code explicitly] | not run |
| 4 | Sign out, then sign in with the same email in another letter case | 200 | not run |
| 5 | `GET <public-url>/auth/me` without a session | 401 `not_authenticated` | not run |
| 6 | `POST <public-url>/auth/sign-in` without the `X-XSRF-TOKEN` header | 403 `csrf_invalid` | not run |
| 7 | From a page on another origin, call the API (browser developer console) | The browser refuses it (no CORS configuration exists) | not run |
| 8 | Open the served files (`/`, `/index.html`, `/js/`, `/css/`, `/config.js`) and look for secrets | No secret, password, connection string or token in any served file | not run |
| 9 | Create an account, trigger a manual redeploy, then sign in again with it | The account still exists (data persists in Neon); the old session is gone (401 `session_expired`) | not run |
| 10 | Create a second fictitious account and look at `/auth/me` and the project list | It sees nothing of the first account | not run |
| 10a | With the first account create a workspace, a project, a task and a comment; then, with the second account, try to read them | The first account can create each (FR-02, INC-15, INC-16); the second account sees none of them and gets the refusals of the access rules | not run |
| 11 | Sign-up limit: send the sixth sign-up request within an hour (only after item 12 is understood) | 429 `too_many_requests` with `Retry-After`; this also locks sign-up for others for the rest of the window | not run |
| 12 | Which client address Render's proxy presents (TD-13) with the approved `native` trial: compare two networks, together with the `XSRF-TOKEN` `Secure` flag of check 3 | Recorded by the decision-maker; TD-13 stays pending until recorded | not run |
| 13 | Remove the test accounts (SQL on Neon) | Needs approval (C1) | not run |

## Troubleshooting

All causes for Render and Neon are hypotheses [TO CONFIRM]; none has been seen.

| Symptom | Likely cause and what to check |
|---|---|
| The first request after a pause is very slow or times out | Cold start of the free service [TO CONFIRM]. Wait and retry; the Neon database may also be waking up |
| 401 `session_expired` after a pause or a deploy | The application restarted and sessions are in memory (TD-03). Sign in again. Expected |
| 429 `too_many_requests` for people who did nothing wrong | Everyone behind the proxy looks like one address (TD-13) and the shared limit is used up. Check `SERVER_FORWARD_HEADERS_STRATEGY` and wait for the window (`Retry-After`); a restart also clears the counters |
| The application does not start, log shows a connection or SSL error | Wrong `MATRIZ_DB_URL`, user or password; the `sslmode` value missing, or Neon's certificate chain not accepted by the JVM trust store (fix the trust store; do not switch to `sslmode=require`); the pooled instead of the direct host; the Neon database suspended or the role lacks rights. Compare with [Neon setup](#neon-setup) |
| Flyway error at start | Read the message. V4 and V5 stop startup on bad existing data (see the migration step). The data is not deleted |
| Sign-in answers 200 but the next request gets 401, or the cookie is not sent | The session cookie is Secure (`MATRIZ_COOKIE_SECURE=true`) and the page is opened over plain HTTP: use the HTTPS address. Also check that a script blocker or another origin is not involved |
| The browser does not keep the session on the local Compose stack | Compose sets `MATRIZ_COOKIE_SECURE=false` by default; do not set it to `true` for `http://localhost` |
| Deploy never becomes healthy, or Render reports no open port | `PORT` and `MATRIZ_PORT` differ, so the application listens on a port Render does not route to; or the health check path is not `/config.js` [TO CONFIRM: how Render detects the port] |
| CORS errors in the browser console | Expected when a page from another origin calls the API: no CORS configuration exists and none is planned (same-origin deployment) |
| 413 on `/auth/` routes | The body is over 16 KB |

## Not decided or not verified

TD-05, TD-06 and TD-13 are pending; the exact Render region name, PD-25 and PD-26 are open. Not verified [TO CONFIRM]: everything about Render and Neon behaviour (free-plan limits and cold start, default port, Blueprint field names, region names, Neon storage, compute and restore window, pooled versus direct endpoints and Flyway on them, TLS and server name indication, Render's proxy addresses); `render.yaml` against Render's validation; that the image builds and starts on Render; what happens to the in-memory sessions and counters at a Render restart (they are lost, which is accepted under TD-03).
