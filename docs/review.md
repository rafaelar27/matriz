# Matriz — Code Review and Access-Control Review

> **Date:** 2026-11-18. **Status:** review done; findings S-01, S-05, R-01 and R-02 fixed; the rest deferred or accepted as listed below. Review IDs (R-xx, S-xx) belong to this review only. Older audit IDs in [verification.md](verification.md) and [test-plan.md](test-plan.md) are separate.

## Scope and environment

- **Code reviewed:** commits `b92e516..HEAD` (INC-10 rule extractions and tests, the shared `PageParams` refactor, the `createTask` Location fix, the Playwright end-to-end test in `e2e/`). The access-control analysis covers the project, task, assignee and comment API as it stands at `HEAD`.
- **Environment:** local only. A scratch PostgreSQL 16 on `localhost:5432` (databases `matriz` and `matriz_test`), the application on `localhost:8080`, and fictitious demo accounts: Ana (Project Manager in Acme Studio, owner of "Website redesign") as User A; Dan (Northwind Lab only) as User B; Ben, Vera, Carla and Eva for same-workspace and empty-workspace checks. Passwords were random values kept outside the repository and never printed.
- **Method:** the `reviewer` agent read the diff; the `security-auditor` agent sent direct HTTP requests; the `tester` agent wrote the regression tests. Results below were checked as stated in "Verifications executed".

## Findings

No Critical, High or Medium finding. Status: Confirmed (evidence in code or output), Suspected (needs a decision or a test not run), False positive.

| ID | Type | File and location | Evidence | Impact | Severity | Status | Decision |
|---|---|---|---|---|---|---|---|
| S-01 | Bug / Error handling | `shared/PageParams.java`; `ProjectService.list`, `TaskService.list` | `GET /projects?page=2147483647&size=100` and the same on `/projects/{id}/tasks` answered 500 ("Page offset exceeds Integer.MAX_VALUE"), reproduced twice | Contract says 422 for a bad page; log noise; no data exposed | Low | Confirmed | **Fixed** (422 on field `page`; `openapi.yaml` page description updated) |
| S-05 | Error handling | `accounts/AuthController.java` (`signIn`) | The broad `catch (AuthenticationException)` turned an internal lookup failure (database down) into 401 `invalid_credentials`; the unit test failed before the fix | A user is told the password is wrong during an outage | Low | Confirmed | **Fixed** (`InternalAuthenticationServiceException` propagates; other failures still give the generic 401) |
| R-01 | Missing test | `shared/PageParams.java` | No unit test existed | A later edit could break both list endpoints unnoticed | Low | Confirmed | **Fixed** (`PageParamsTest`, 8 tests) |
| R-02 | Missing test | `ProjectApiTest` (createProject) | Only `header().exists("Location")` was asserted | A wrong Location value would pass | Low | Confirmed | **Fixed** (exact `/projects/{id}` assertion) |
| S-02 | Error handling | `SecurityConfig`, `ApiExceptionHandler` | Unknown routes, 405 and 500 return the framework default body, not `ApiError`; no stack trace | Format differs from the contract | Low | Confirmed, already known (INC-08 audit S-03) | **Accepted risk**, unchanged |
| S-03 | Validation order | `ApiExceptionHandler` | A foreign user sending an unknown field to `PATCH /projects/{id}` gets 422, not 404; same answer as for a missing id | Status differs from API-A3 for bad bodies; existence not revealed | Low | Confirmed, already known (INC-08 audit S-02) | **Accepted risk**, unchanged |
| S-04 | Hardening | `SecurityConfig` | No `Content-Security-Policy` header; no XSS sink found in `app.js` | Defence in depth only | Low | Confirmed, already known | **Deferred**: a security setting change needs approval (C1) |
| S-06 | Hardening | `SecurityConfig` | `XSRF-TOKEN` cookie has no `Secure` flag tied to `MATRIZ_COOKIE_SECURE`; not tested over HTTPS | Matters only behind HTTPS-terminating hosting | Low | Suspected | **Deferred** until hosting is decided (TD-02) |
| S-07 | Validation | `TaskService.owner`, `ProjectRepository.isProjectMember` | A task owner is checked as a project member without checking that the member is active; project owners and assignees require active members. Code read; not run, because no demo member is deactivated | A deactivated member could become a task owner (BR-09, SP-04) | Low | Suspected | **Deferred**: the rule needs a product decision, then a fix and test |
| R-03 | Validation | `ProjectService`, `TaskService` (description) | Descriptions are not trimmed; the documentation does not decide it | A whitespace-only description is stored as is | Low | Suspected | **Deferred**: needs a product decision (W2) |
| F-1 | CSRF | audit request 178 | "Bad token" `PATCH` returned 200 because the script also sent the valid token; retests with only a bad or empty token gave 403 | None | — | False positive (test error) | **Rejected**. The accidental rename it caused was reverted. |

## Access-control (IDOR) tests

Direct API requests with two demo accounts, run by the `security-auditor` agent. The full request and response log is in the appendix. Results by group:

| Group | What was tried | Result |
|---|---|---|
| T1 | User B (Dan, and Eva) reads User A's project, tasks, task, assignees, eligible assignees, comments, progress and workspace members | 404 everywhere; progress returned an empty list; a non-existent id gave the same 404 body |
| T2 | User B modifies or deletes the same resources, creates tasks and comments, assigns | 404 everywhere; owner or assignee from another workspace gave 422 |
| T3 | A task id under another project id, across workspaces and inside one workspace, for read, edit, delete, assignees and comments | 404 everywhere |
| T4 | Client-supplied `created_by_member_id`, `author_member_id`, `created_at`, `workspace_id`, `project_id`, `task_id` (also camelCase), identity headers and query strings | 422 or ignored; the identity always came from the session |
| T5 | Project list, `workspace_id`, search, filters, paging and progress for foreign data | Nothing from other workspaces |
| T6 | Response bodies | No password hash, no session id, no other user's email |
| T7 | Malformed ids, SQL-like input, boundary and huge paging, bad bodies | 4xx, except S-01 (500) |
| T8 | Every contract operation without a session; missing, bogus, empty or another user's CSRF token | 401 without a session; 403 `csrf_invalid` for a bad token |
| T9 | Same-workspace role rules for Ben, Vera, Carla, Ana in Northwind Lab and Dan | 403 as documented, including the provisional rules PD-01, PD-03, PD-17 and PD-20 |
| T10 | Body validated before access for a foreign project | 422 or 400 (S-03) |
| T11 | Sign-out and reuse of the old or a forged session cookie | 401 `session_expired` |

**Secrets:** a search of the working tree and all git history for secret-like patterns found only placeholders (`.env.example`, an `openapi.yaml` example), environment-variable reads, and one fixed, fictitious test password constant in `src/test/java/com/matriz/ApiTestSupport.java`. `.env` was never committed and is ignored by Git. No log held a password, hash or session cookie.

## Static analysis

No static-analysis tool is configured (`pom.xml` has JaCoCo, which measures coverage only). None was run and none was installed. A dependency vulnerability scan was not run; the OWASP dependency-check Maven plugin was proposed and has not been approved.

## Fixes and regression tests

| Finding | Production change | Regression tests | Before the fix | After the fix |
|---|---|---|---|---|
| S-01 | `PageParams.page(value, size, message)` added; both list controllers read the size first and then the page; `openapi.yaml` page description (both operations) | `ProjectApiTest.hugePageIsRefusedWithValidationFailed`, `TaskApiTest.hugePageIsRefusedWithValidationFailed`, two guard tests, 3 overload tests in `PageParamsTest` | The two API tests failed with the real exception; the other 82 of 84 passed | Pass |
| S-05 | `AuthController.signIn` rethrows `InternalAuthenticationServiceException` | `AuthControllerSignInTest` (3 tests) | `internalAuthenticationServiceExceptionPropagates` failed | Pass |
| R-01 | None | `PageParamsTest` (8 tests) | Pass (tests of existing behaviour) | Pass |
| R-02 | None | Exact Location assertion in the createProject test | Pass (the code was already correct) | Pass |

Full suite after the fixes: `mvn verify` on `matriz_test`, 338 tests, 0 failures, `BUILD SUCCESS` (323 before, plus 15 new).

## Verifications executed

- `mvn verify` before any change: 323 tests, 0 failures.
- The before and after runs of the S-01 and S-05 tests described above, and the final `mvn verify`.
- About 230 direct API requests (appendix), and the reproduction of S-01 by hand.
- Repository and history search for secrets; log search for passwords, hashes and cookies.

## Verifications still pending

- A re-read of the data after the attacks to prove that refused requests changed nothing: the database stopped during the audit, so this rests on the 4xx answers and the code. The one known change (the accidental rename) was reverted and re-read.
- Whether spoofed fields are ignored on a request that succeeds, and a Project Manager who is not a project member editing or assigning (PD-01, PD-20): not run live.
- A deactivated member as a task owner (S-07), idle session expiry, session fixation and sign-in timing, `Secure` cookie flags over HTTPS, a browser XSS test, and a dependency vulnerability scan.
- The Playwright end-to-end test (not run; the browser download is blocked in this environment).
- Static analysis (no tool configured).
- The `docs/` pages were not re-read in full by the reviewing agents, so documentation drift beyond the items above was not checked.

## Open questions and proposed documentation updates

- **Q1 (S-07, BR-09, SP-04):** must a task owner be an active project member, as project owners and assignees must be?
- **Q2 (R-03):** are project and task descriptions trimmed, or cleared when they hold only spaces?
- **Q3 (PD-01, PD-20):** may a Project Manager who is not a project member edit tasks, change owners and assign members?
- **Proposed (README, "Run the verification check"):** the expected result "Tests run: 323" is now 338.
- **Proposed (README, documentation table and status):** add a row for this file.

## Appendix: API requests and responses

Ids are shortened: `d01` = `d0000000-0000-0000-0000-000000000001`, `e0x` = a task, `c0x` = a member, `b0x` = a workspace, `a0x` = an account. `–` means no session. The table is the auditor's log as recorded; it lists rows 1 to 228.

| # | Test | User | Request | Status | Body excerpt |
|---|---|---|---|---|---|
| 1 | T1 | dan | GET /projects/d01 | 404 | not_found |
| 2 | T1 | dan | GET /projects/d01/tasks | 404 | not_found |
| 3 | T1 | dan | GET /projects/d01/tasks/e01 | 404 | not_found |
| 4 | T1 | dan | GET /projects/d01/tasks/e01/assignees | 404 | not_found |
| 5 | T1 | dan | GET /projects/d01/tasks/e01/eligible-assignees | 404 | not_found |
| 6 | T1 | dan | GET /projects/d01/tasks/e01/comments | 404 | not_found |
| 7 | T1 | dan | GET /progress?project_id=d01 | 200 | [] |
| 8 | T1 | dan | GET /workspaces/b01/members | 404 | not_found |
| 9 | T1 | eva | GET /projects/d01 | 404 | not_found |
| 10 | T1 | eva | GET /projects/d01/tasks | 404 | not_found |
| 11 | T1 | eva | GET /projects/d01/tasks/e01 | 404 | not_found |
| 12 | T1 | eva | GET /projects/d01/tasks/e01/assignees | 404 | not_found |
| 13 | T1 | eva | GET /projects/d01/tasks/e01/eligible-assignees | 404 | not_found |
| 14 | T1 | eva | GET /projects/d01/tasks/e01/comments | 404 | not_found |
| 15 | T1 | eva | GET /progress?project_id=d01 | 200 | [] |
| 16 | T1 | eva | GET /workspaces/b01/members | 404 | not_found |
| 17 | T1 | dan | GET /projects/dff | 404 | not_found |
| 18 | T1 | dan | GET /projects/dff/tasks/e01 | 404 | not_found |
| 19 | T1 | dan | GET /workspaces/bff/members | 404 | not_found |
| 20 | T2 | dan | PATCH /projects/d01 | 404 | not_found |
| 21 | T2 | dan | PATCH /projects/d01/tasks/e01 | 404 | not_found |
| 22 | T2 | dan | PATCH /projects/d01/tasks/e01 | 404 | not_found |
| 23 | T2 | dan | POST /projects/d01/tasks/e02/assignees | 404 | not_found |
| 24 | T2 | dan | DELETE /projects/d01/tasks/e01/assignees/c03 | 404 | not_found |
| 25 | T2 | dan | POST /projects/d01/tasks/e01/comments | 404 | not_found |
| 26 | T2 | dan | POST /projects/d01/tasks | 404 | not_found |
| 27 | T2 | dan | DELETE /projects/d01/tasks/e02 | 404 | not_found |
| 28 | T2 | dan | DELETE /projects/d01 | 404 | not_found |
| 29 | T2 | eva | PATCH /projects/d01 | 404 | not_found |
| 30 | T2 | eva | PATCH /projects/d01/tasks/e01 | 404 | not_found |
| 31 | T2 | eva | PATCH /projects/d01/tasks/e01 | 404 | not_found |
| 32 | T2 | eva | POST /projects/d01/tasks/e02/assignees | 404 | not_found |
| 33 | T2 | eva | DELETE /projects/d01/tasks/e01/assignees/c03 | 404 | not_found |
| 34 | T2 | eva | POST /projects/d01/tasks/e01/comments | 404 | not_found |
| 35 | T2 | eva | POST /projects/d01/tasks | 404 | not_found |
| 36 | T2 | eva | DELETE /projects/d01/tasks/e02 | 404 | not_found |
| 37 | T2 | eva | DELETE /projects/d01 | 404 | not_found |
| 38 | T2 | dan | POST /projects | 404 | not_found |
| 39 | T2 | dan | PATCH /projects/d03 | 422 | validation_failed: The project could not be saved. |
| 40 | T2 | dan | POST /projects/d03/tasks/e05/assignees | 422 | validation_failed: The members could not be assigned. |
| 41 | T3 | ana | GET /projects/d01/tasks/e05 | 404 | not_found |
| 42 | T3 | ana | GET /projects/d01/tasks/e05/assignees | 404 | not_found |
| 43 | T3 | ana | GET /projects/d01/tasks/e05/eligible-assignees | 404 | not_found |
| 44 | T3 | ana | GET /projects/d01/tasks/e05/comments | 404 | not_found |
| 45 | T3 | ana | GET /projects/d03/tasks/e01 | 404 | not_found |
| 46 | T3 | ana | GET /projects/d03/tasks/e01/assignees | 404 | not_found |
| 47 | T3 | ana | GET /projects/d03/tasks/e01/eligible-assignees | 404 | not_found |
| 48 | T3 | ana | GET /projects/d03/tasks/e01/comments | 404 | not_found |
| 49 | T3 | ana | PATCH /projects/d01/tasks/e05 | 404 | not_found |
| 50 | T3 | ana | PATCH /projects/d01/tasks/e05 | 404 | not_found |
| 51 | T3 | ana | POST /projects/d01/tasks/e05/comments | 404 | not_found |
| 52 | T3 | ana | POST /projects/d01/tasks/e05/assignees | 404 | not_found |
| 53 | T3 | ana | DELETE /projects/d01/tasks/e05/assignees/c03 | 404 | not_found |
| 54 | T3 | ana | DELETE /projects/d01/tasks/e05 | 404 | not_found |
| 55 | T3 | ana | PATCH /projects/d03/tasks/e01 | 404 | not_found |
| 56 | T3 | ana | DELETE /projects/d03/tasks/e01 | 404 | not_found |
| 57 | T3 | ben | GET /projects/d01/tasks/e05 | 404 | not_found |
| 58 | T3 | ben | GET /projects/d01/tasks/e05/comments | 404 | not_found |
| 59 | T3 | ana | GET /projects/d01/tasks/e04 | 404 | not_found |
| 60 | T3 | ana | GET /projects/d01/tasks/e04/comments | 404 | not_found |
| 61 | T3 | ana | GET /projects/d01/tasks/e04/assignees | 404 | not_found |
| 62 | T3 | ana | GET /projects/d01/tasks/e04/eligible-assignees | 404 | not_found |
| 63 | T3 | ana | PATCH /projects/d01/tasks/e04 | 404 | not_found |
| 64 | T3 | ana | POST /projects/d01/tasks/e04/assignees | 404 | not_found |
| 65 | T3 | ana | POST /projects/d01/tasks/e04/comments | 404 | not_found |
| 66 | T3 | ben | DELETE /projects/d01/tasks/e04 | 404 | not_found |
| 67 | T3 | ben | PATCH /projects/d01/tasks/e04 | 404 | not_found |
| 68 | T3 | ana | DELETE /projects/d01/tasks/e04/assignees/c04 | 404 | not_found |
| 69 | T4 | ben | POST /projects/d01/tasks | 422 | validation_failed: The request could not be processed. |
| 70 | T4 | ben | POST /projects/d01/tasks | 422 | validation_failed: The request could not be processed. |
| 71 | T4 | ben | POST /projects/d01/tasks | 422 | validation_failed: The request could not be processed. |
| 72 | T4 | ben | POST /projects/d01/tasks | 422 | validation_failed: The request could not be processed. |
| 73 | T4 | ben | POST /projects/d01/tasks | 422 | validation_failed: The request could not be processed. |
| 74 | T4 | ben | POST /projects/d01/tasks | 422 | validation_failed: The request could not be processed. |
| 75 | T4 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The request could not be processed. |
| 76 | T4 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The request could not be processed. |
| 77 | T4 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The request could not be processed. |
| 78 | T4 | ana | PATCH /projects/d01 | 422 | validation_failed: The request could not be processed. |
| 79 | T4 | ana | PATCH /projects/d01 | 422 | validation_failed: The request could not be processed. |
| 80 | T4 | ana | PATCH /projects/d01 | 422 | validation_failed: The request could not be processed. |
| 81 | T4 | ana | PATCH /projects/d01 | 422 | validation_failed: The request could not be processed. |
| 82 | T4 | ana | POST /projects | 422 | validation_failed: The request could not be processed. |
| 83 | T4 | ana | POST /projects | 422 | validation_failed: The request could not be processed. |
| 84 | T4 | ana | POST /projects/d01/tasks/e01/comments | 422 | validation_failed: The request could not be processed. |
| 85 | T4 | ana | POST /projects/d01/tasks/e01/comments | 422 | validation_failed: The request could not be processed. |
| 86 | T4 | ana | POST /projects/d01/tasks/e01/comments | 422 | validation_failed: The request could not be processed. |
| 87 | T4 | ana | POST /projects/d01/tasks/e02/assignees | 422 | validation_failed: The request could not be processed. |
| 88 | T4 | ana | POST /projects/d01/tasks/e02/assignees | 422 | validation_failed: The request could not be processed. |
| 89 | T4 | dan | GET /projects/d01 | 404 | not_found |
| 90 | T4 | dan | GET /projects/d01?account_id=a01&member_id=c02 | 404 | not_found |
| 91 | T4 | dan | GET /projects?workspace_id=b01 | 200 | items:[] "total":0 |
| 92 | T4 | dan | GET /projects?workspace_id=b01&q=Website | 200 | items:[] "total":0 |
| 93 | T5 | dan | GET /projects?size=100 | 200 | items: Lab inventory |
| 94 | T5 | dan | GET /projects?q=website | 200 | items:[] "total":0 |
| 95 | T5 | dan | GET /projects?q=e | 200 | items: Lab inventory |
| 96 | T5 | dan | GET /projects?q=%25 | 200 | items:[] "total":0 |
| 97 | T5 | dan | GET /projects?q=_ | 200 | items:[] "total":0 |
| 98 | T5 | dan | GET /projects?page=1&size=1 | 200 | items:[] "total":1 |
| 99 | T5 | eva | GET /projects?size=100 | 200 | items:[] "total":0 |
| 100 | T5 | ben | GET /projects?size=100 | 200 | items: Trade fair stand |
| 101 | T5 | ben | GET /projects?workspace_id=b02 | 200 | items:[] "total":0 |
| 102 | T5 | dan | GET /projects/d03/tasks?owner_member_id=c03 | 200 | items:[] "total":0 |
| 103 | T5 | dan | GET /projects/d03/tasks?owner_member_id=c06 | 200 | items: Count lab equipment |
| 104 | T5 | ben | GET /progress?project_id=d01&project_id=d03&project_id=d02 | 200 | [{"project_id":"d01","done":1,"total":3},{"project_id":"d02","done":0,… |
| 105 | T5 | dan | GET /progress?project_id=d01&project_id=d03 | 200 | [{"project_id":"d03","done":0,"total":1}]… |
| 106 | T5 | dan | GET /progress?project_id=d03&project_id=d03&project_id=d03 | 200 | [{"project_id":"d03","done":0,"total":1}]… |
| 107 | T5 | dan | GET /progress?project_id=d01,d03 | 200 | [{"project_id":"d03","done":0,"total":1}]… |
| 108 | T5 | ben | GET /projects/d01/tasks/e01/eligible-assignees | 200 | [{"member_id":"c02","name":"Ana Demo","role":"Project Manager"},{"memb… |
| 109 | T5 | ana | GET /workspaces/b02/members | 200 | [{"member_id":"c06","name":"Ana Demo","role":"Team Member"},{"member_i… |
| 110 | T6 | ana | GET /auth/me | 200 | {"account_id":"a01","name":"Ana Demo","email":"ana@example.com","membe… |
| 111 | T6 | ana | GET /projects/d01/tasks/e01/comments | 200 | [{"comment_id":"f01","task_id":"e01","author_member_id":"c02","author_… |
| 112 | T6 | ana | GET /projects/d01/tasks/e01/assignees | 200 | [] |
| 113 | T6 | ana | GET /workspaces/b01/members | 200 | [{"member_id":"c02","name":"Ana Demo","role":"Project Manager"},{"memb… |
| 114 | T6 | ana | GET /projects/d01/tasks/e01/eligible-assignees | 200 | [{"member_id":"c02","name":"Ana Demo","role":"Project Manager"},{"memb… |
| 115 | T7 | ana | GET /projects/not-a-uuid | 404 | not_found |
| 116 | T7 | ana | GET /projects/'%20OR%201=1-- | 404 | not_found |
| 117 | T7 | ana | GET /projects/d01/tasks/xyz | 404 | not_found |
| 118 | T7 | ana | GET /projects/d01/tasks/e01/assignees/notuuid | 405 | framework default body: Method Not Allowed (no trace) |
| 119 | T7 | ana | GET /projects?q=%27%20OR%201%3D1-- | 200 | items:[] "total":0 |
| 120 | T7 | ana | GET /projects?q=%27%3B%20DROP%20TABLE%20project%3B-- | 200 | items:[] "total":0 |
| 121 | T7 | ana | GET /projects?q=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa…(113 chars) | 422 | validation_failed: The projects could not be listed. |
| 122 | T7 | ana | GET /projects?page=2147483647&size=100 | 500 | framework default body: Internal Server Error (no trace) |
| 123 | T7 | ana | GET /projects?page=21474836&size=100 | 200 | items:[] "total":3 |
| 124 | T7 | ana | GET /projects?page=99999999999 | 422 | validation_failed: The projects could not be listed. |
| 125 | T7 | ana | GET /projects?size=1000 | 422 | validation_failed: The projects could not be listed. |
| 126 | T7 | ana | GET /projects?size=-1 | 422 | validation_failed: The projects could not be listed. |
| 127 | T7 | ana | GET /projects?page=-1 | 422 | validation_failed: The projects could not be listed. |
| 128 | T7 | ana | GET /projects/d01/tasks?page=2147483647&size=100 | 500 | framework default body: Internal Server Error (no trace) |
| 129 | T7 | ana | GET /projects/d01/tasks?status=%27%20OR%201%3D1 | 422 | validation_failed: The tasks could not be listed. |
| 130 | T7 | ana | GET /projects/d01/tasks?owner_member_id=%27%20OR%201%3D1 | 422 | validation_failed: The tasks could not be listed. |
| 131 | T7 | ana | GET /projects?workspace_id=zzz | 422 | validation_failed: The projects could not be listed. |
| 132 | T7 | ana | GET /progress | 422 | validation_failed: The progress could not be read. |
| 133 | T7 | ana | GET /progress?project_id=zzz | 422 | validation_failed: The progress could not be read. |
| 134 | T7 | ana | GET /progress?project_id=d01&project_id=d01&project_id=d01&proje…(4857 chars) | 422 | validation_failed: The progress could not be read. |
| 135 | T7 | vera | PATCH /projects/d01/tasks/e01 | 400 | malformed_request |
| 136 | T7 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The request could not be processed. |
| 137 | T7 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The request could not be processed. |
| 138 | T7 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The task could not be saved. |
| 139 | T7 | ana | PATCH /projects/d01/tasks/e02 | 400 | malformed_request |
| 140 | T7 | ana | POST /projects/d01/tasks/e02/assignees | 422 | validation_failed: The request could not be processed. |
| 141 | T7 | ana | POST /projects/d01/tasks/e02/assignees | 422 | validation_failed: The request could not be processed. |
| 142 | T7 | ana | PATCH /projects/d01/tasks/e02 | 422 | validation_failed: The task could not be saved. |
| 143 | T7 | ana | PUT /projects/d01 | 405 | framework default body: Method Not Allowed (no trace) |
| 144 | T7 | ana | GET /nonexistent | 404 | framework default body: Not Found (no trace) |
| 145 | T7 | ana | GET /actuator | 404 | framework default body: Not Found (no trace) |
| 146 | T7 | ana | GET /actuator/env | 404 | framework default body: Not Found (no trace) |
| 147 | T7 | ana | GET /error | 500 | framework default body: None (no trace) |
| 148 | T7 | ana | TRACE /projects/d01 | 400 | (empty) |
| 149 | T8-noauth | (none) | GET /auth/me | 401 | not_authenticated |
| 150 | T8-noauth | (none) | POST /auth/sign-out | 403 | csrf_invalid |
| 151 | T8-noauth | (none) | GET /projects | 401 | not_authenticated |
| 152 | T8-noauth | (none) | POST /projects | 403 | csrf_invalid |
| 153 | T8-noauth | (none) | GET /projects/d01 | 401 | not_authenticated |
| 154 | T8-noauth | (none) | PATCH /projects/d01 | 403 | csrf_invalid |
| 155 | T8-noauth | (none) | DELETE /projects/d01 | 403 | csrf_invalid |
| 156 | T8-noauth | (none) | GET /progress?project_id=d01 | 401 | not_authenticated |
| 157 | T8-noauth | (none) | GET /workspaces/b01/members | 401 | not_authenticated |
| 158 | T8-noauth | (none) | GET /projects/d01/tasks | 401 | not_authenticated |
| 159 | T8-noauth | (none) | POST /projects/d01/tasks | 403 | csrf_invalid |
| 160 | T8-noauth | (none) | GET /projects/d01/tasks/e01 | 401 | not_authenticated |
| 161 | T8-noauth | (none) | PATCH /projects/d01/tasks/e01 | 403 | csrf_invalid |
| 162 | T8-noauth | (none) | DELETE /projects/d01/tasks/e02 | 403 | csrf_invalid |
| 163 | T8-noauth | (none) | GET /projects/d01/tasks/e01/eligible-assignees | 401 | not_authenticated |
| 164 | T8-noauth | (none) | GET /projects/d01/tasks/e01/assignees | 401 | not_authenticated |
| 165 | T8-noauth | (none) | POST /projects/d01/tasks/e01/assignees | 403 | csrf_invalid |
| 166 | T8-noauth | (none) | DELETE /projects/d01/tasks/e01/assignees/c04 | 403 | csrf_invalid |
| 167 | T8-noauth | (none) | GET /projects/d01/tasks/e01/comments | 401 | not_authenticated |
| 168 | T8-noauth | (none) | POST /projects/d01/tasks/e01/comments | 403 | csrf_invalid |
| 169 | T8-noauth | (none) | GET /auth/csrf | 204 | (empty) |
| 170 | T8-noauth | (none) | GET /config.js | 200 | window.MATRIZ_CONFIG = Object.freeze({ apiBaseUrl: "" }); … |
| 171 | T8-noauth | (none) | GET / | 200 | <!doctype html> <html lang="en"> <head> <meta charset="utf-8"> <meta n… |
| 172 | T8-nocsrf | ana | PATCH /projects/d01 | 403 | csrf_invalid |
| 173 | T8-nocsrf | ana | DELETE /projects/d01/tasks/e02 | 403 | csrf_invalid |
| 174 | T8-nocsrf | ana | POST /projects/d01/tasks/e01/comments | 403 | csrf_invalid |
| 175 | T8-nocsrf | ana | POST /projects/d01/tasks/e02/assignees | 403 | csrf_invalid |
| 176 | T8-nocsrf | ana | POST /projects | 403 | csrf_invalid |
| 177 | T8-nocsrf | ana | POST /auth/sign-out | 403 | csrf_invalid |
| 178 | T8-badcsrf | ana | PATCH /projects/d01 | 200 | {"project_id":"d01","workspace_id":"b01","workspace_name":"Acme Studio… |
| 179 | T8-badcsrf | ana | POST /projects/d01 | 403 | csrf_invalid |
| 180 | T8-badcsrf | ana | GET /projects/d01?_method=DELETE | 200 | {"project_id":"d01","workspace_id":"b01","workspace_name":"Acme Studio… |
| — | NOTE | | | | Previous T8-badcsrf PATCH sent BOTH the valid token (helper) and a bogus header; curl sends both, server read the valid one -> test artifact, project renamed 'Website redesign' -> 'CSRF test' (UNINTENDED CHANGE, not restored) |
| 181 | T8-badcsrf-retest | ana | PATCH /projects/d01 | 403 | csrf_invalid |
| 182 | T8-badcsrf-retest | ana | PATCH /projects/d01 | 403 | csrf_invalid |
| 183 | T8-badcsrf-retest | ana | PATCH /projects/d01?_csrf=deadbeef | 403 | csrf_invalid |
| 184 | T9-role | ben | PATCH /projects/d01 | 403 | forbidden |
| 185 | T9-role | ben | POST /projects | 403 | forbidden |
| 186 | T9-role | ben | POST /projects/d01/tasks/e02/assignees | 403 | forbidden |
| 187 | T9-role | ben | DELETE /projects/d01/tasks/e02/assignees/c04 | 403 | forbidden |
| 188 | T9-role | vera | PATCH /projects/d01 | 403 | forbidden |
| 189 | T9-role | vera | POST /projects | 403 | forbidden |
| 190 | T9-role | vera | POST /projects/d01/tasks/e02/assignees | 403 | forbidden |
| 191 | T9-role | vera | DELETE /projects/d01/tasks/e02/assignees/c04 | 403 | forbidden |
| 192 | T9-role | carla | PATCH /projects/d01 | 403 | forbidden |
| 193 | T9-role | carla | POST /projects | 403 | forbidden |
| 194 | T9-role | carla | POST /projects/d01/tasks/e02/assignees | 403 | forbidden |
| 195 | T9-role | carla | DELETE /projects/d01/tasks/e02/assignees/c04 | 403 | forbidden |
| 196 | T9-role | vera | DELETE /projects/d01 | 403 | forbidden |
| 197 | T9-role | carla | DELETE /projects/d01 | 403 | forbidden |
| 198 | T9-role | ben | DELETE /projects/d01 | 403 | forbidden |
| 199 | T9-role | ben | DELETE /projects/d01/tasks/e02 | 403 | forbidden |
| 200 | T9-role | vera | DELETE /projects/d01/tasks/e03 | 403 | forbidden |
| 201 | T9-role | carla | DELETE /projects/d01/tasks/e02 | 403 | forbidden |
| 202 | T9-role | ben | PATCH /projects/d01/tasks/e02 | 403 | forbidden |
| 203 | T9-role | ben | PATCH /projects/d01/tasks/e01 | 403 | forbidden |
| 204 | T9-role | ana | PATCH /projects/d01/tasks/e01 | 403 | forbidden |
| 205 | T9-role | carla | PATCH /projects/d01/tasks/e01 | 403 | forbidden |
| 206 | T9-role | vera | PATCH /projects/d01/tasks/e01 | 403 | forbidden |
| 207 | T9-role | vera | POST /projects/d01/tasks/e01/comments | 403 | forbidden |
| 208 | T9-role | carla | POST /projects/d01/tasks/e01/comments | 403 | forbidden |
| 209 | T9-role | vera | POST /projects/d01/tasks | 403 | forbidden |
| 210 | T9-role | carla | POST /projects/d01/tasks | 403 | forbidden |
| 211 | T9-role | ben | POST /projects/d01/tasks | 403 | forbidden |
| 212 | T9-role | ana | PATCH /projects/d03 | 403 | forbidden |
| 213 | T9-role | ana | DELETE /projects/d03 | 403 | forbidden |
| 214 | T9-role | ana | POST /projects/d03/tasks | 403 | forbidden |
| 215 | T9-role | ana | POST /projects/d03/tasks/e05/assignees | 403 | forbidden |
| 216 | T9-role | dan | POST /projects/d03/tasks | 403 | forbidden |
| 217 | T9-role | dan | POST /projects/d03/tasks/e05/comments | 403 | forbidden |
| 218 | T9-role | dan | PATCH /projects/d03/tasks/e05 | 403 | forbidden |
| 219 | T9-role | dan | DELETE /projects/d03/tasks/e05 | 403 | forbidden |
| 220 | T10 | dan | PATCH /projects/d01 | 422 | validation_failed: The request could not be processed. |
| 221 | T10 | dan | PATCH /projects/d01 | 400 | malformed_request |
| 222 | T11-signout | eva | POST /auth/sign-out | 204 | (empty) |
| 223 | T11-signout | eva-old | GET /projects | 401 | session_expired |
| 224 | T11-signout | eva-old | GET /auth/me | 401 | session_expired |
| 225 | T11-signout | eva-old | PATCH /projects/d01 | 401 | session_expired |
| 226 | T11-signout | (none) | GET /projects | 401 | session_expired |
| 227 | T11-signout | (none) | GET /projects;jsessionid=0123456789ABCDEF0123456789ABCDEF | 400 | framework default body: Bad Request (no trace) |
| 228 | T12-dbdown | ben | GET /projects/d01 | 500 | framework default body: Internal Server Error (no trace) |