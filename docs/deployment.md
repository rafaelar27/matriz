# Matriz — Deployment notes (Render and Neon)

> **Status (2026-11-19):** notes for the intended public deployment of the app on Render with a Neon PostgreSQL database, both on free plans. **Nothing has been deployed, and no step here has been run against Render or Neon.** Hosting, region, backup and availability are not decided ([TD-02, TD-05 and TD-06](constraints.md#pending-technical-decisions)); this file records what the code needs, so that those decisions can be taken. The programmer deploys by hand and enters every secret in the Render dashboard. Requirement IDs are defined in [requirements.md](requirements.md); the sign-up rules are in [user-stories.md](user-stories.md#acceptance-criteria) (US-22) and the [decision log](constraints.md#decision-log).

## Before the first deployment

1. **Decide TD-02** (where data is stored, including the region; NFR-02), TD-05 and TD-06, and record them in the decision log (D2).
2. **Check the Neon database first** (read-only): migration V5 fails as a whole, and changes nothing, if two accounts have emails that differ only by letter case. On a new, empty Neon database there is nothing to check.
   ```sql
   SELECT lower(email), count(*) FROM account GROUP BY 1 HAVING count(*) > 1;
   ```
3. **Do not run the seed command on the public database** (decision log 2026-11-18). If a public demo is wanted, it is a separate deployment with its own database.
4. Use Neon's **direct** (non-pooled) connection string for the application, because Flyway applies the migrations when the application starts. How Flyway behaves on Neon's pooled endpoint is not verified.

## Environment variables (set only in the Render dashboard)

Never commit values. `.env.example` holds fictitious placeholders only.

| Variable | Purpose | Notes |
|---|---|---|
| `MATRIZ_DB_URL` | JDBC URL of the Neon database | Direct endpoint; add `sslmode=require` to the URL |
| `MATRIZ_DB_USER`, `MATRIZ_DB_PASSWORD` | Database role and password | **Secrets**: entered by the programmer in the dashboard |
| `MATRIZ_PORT` | HTTP port | Render sets the port it routes to; set this variable to match it |
| `MATRIZ_COOKIE_SECURE` | `true` | The public site is HTTPS, so the session cookie must be Secure |
| `MATRIZ_SESSION_TIMEOUT` | Idle timeout of a session | Optional, default `8h` |
| `MATRIZ_RATE_SIGN_UP_MAX`, `MATRIZ_RATE_SIGN_UP_WINDOW` | Sign-up limit per client address | Optional; defaults 5 per `1h` (NFR-11) |
| `MATRIZ_RATE_SIGN_IN_MAX_PER_IP_AND_EMAIL`, `MATRIZ_RATE_SIGN_IN_MAX_PER_IP`, `MATRIZ_RATE_SIGN_IN_WINDOW` | Failed sign-in limits | Optional; defaults 10, 50 and `15m` |

`MATRIZ_DEMO_PASSWORD` is **not** set on the public service.

## Rate limiting and the client address (TD-13, NFR-11)

- The limits are kept in the memory of the running application. They start again at every restart, and they are only correct with one instance. A free Render service sleeps when idle and restarts, which also ends every session.
- The application ignores forwarded headers (`server.forward-headers-strategy=none`, set explicitly on 2026-11-19), so it sees the address of the direct connection. **Behind Render's proxy, every visitor may therefore look like one address.** Then the sign-up limit (5 per hour) is shared by everyone, one person can use it up, and 50 failed sign-ins lock everyone out of sign-in for 15 minutes. This is a known risk until TD-13 is decided; it has not been checked on Render.
- To read the real client address, TD-13 must be decided and checked on the live service. The candidate is `server.forward-headers-strategy=native`, which trusts `X-Forwarded-For` only from the platform's proxy addresses. A wrong setting either shares one address among all users or lets a client forge its address and avoid the limits. Changing it is a security setting and needs approval (C1).
- When the table of counters is full (20,000 keys), new addresses are refused with 429 until a window ends; existing lockouts are never forgotten. Filling it takes 20,000 distinct addresses.

## Free-tier abuse risk

Anyone can sign up (FR-01). Mass sign-ups fill account rows in Neon's small free storage. A new account cannot create content yet (FR-02 is not built), so each row is small, but there is no tool to delete spam accounts: removing them is SQL on Neon, a destructive action that needs approval (C1). BCrypt is slow by design, so floods of sign-up and sign-in requests can use up the CPU of a small Render instance; the rate limits and the 16 KB body limit are the protection, and they reset at every restart. There is no email verification (PD-25), CAPTCHA or password reset (PD-26).

## Checks after the first deployment (to run, with approval)

Run only with fictitious addresses, each step approved by the programmer first, because they create real rows on the public database. None of them has been run.

1. `GET /config.js` and the sign-in page load over HTTPS; the response sets `MATRIZ_SESSION` with `Secure` and `HttpOnly` after sign-in or sign-up.
2. Sign up, sign out, sign in with the email in another letter case, and reach `/auth/me`.
3. Create a second account and confirm that it sees no data of the first, including through direct API requests (404 for another workspace's ids).
4. Over the sign-up limit: the sixth request in an hour gets 429 with `Retry-After`; check which address Render's proxy presents (see above) before relying on the limit.
5. Remove the test accounts afterwards (SQL on Neon, approval required).

## Not decided or not verified

TD-02, TD-05, TD-06, TD-13, PD-25, PD-26; Flyway on Neon's pooled endpoint; Neon free-plan backup and storage limits; Render's proxy addresses; what happens to the in-memory sessions and counters at a Render restart (they are lost, which is accepted under TD-03).
