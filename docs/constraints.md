# Matriz — Constraints, Assumptions and Pending Decisions

> **Status:** draft, pending client validation. Requirements are in [requirements.md](requirements.md) and user stories in [user-stories.md](user-stories.md).

## Known constraints

These are facts about the situation, not assumptions.

| ID | Constraint |
|---|---|
| KC-01 | The client is not available to answer clarification questions. While that is the case, the project decision-maker makes pending decisions and confirms or rejects assumptions. Everything beyond the known facts (KF-01 to KF-06 in [requirements.md](requirements.md#known-facts)) and the items marked **Confirmed** below is an assumption until confirmed. |
| KC-02 | The only sources are the client's one-sentence description and the project description. |
| KC-03 | The architectural style and the technology stack are decided (see the [decision log](#decision-log)). Development is under way (see [implementation-plan.md](implementation-plan.md)); hosting has not been chosen. Open choices are listed under [Pending technical decisions](#pending-technical-decisions). |
| KC-04 | No budget has been given. The working assumption is AQ29. |
| KC-05 | No timeline has been given. The working assumption is AQ30. |
| KC-06 | The legal availability of the name "Matriz" has not been checked. The working assumption is AQ33. |
| KC-07 | No decision-maker other than the client has been identified. The working assumption is AQ21. While the client is unavailable, decisions are made by the project decision-maker (KC-01). |
| KC-08 | The development team is one person, the project decision-maker (stated by the project decision-maker on 2026-11-05). |

## Assumptions made

Every row is an **assumption** unless it is marked **Confirmed**; confirmed rows are recorded in the [decision log](#decision-log). AQ numbers match the clarification questions Q1–Q33 from the initial analysis. They replace the initial analysis's provisional assumptions A1–A8: A1 → AQ22, A2 and A6 → AQ3, A3 → AQ7 and AQ8, A4 → AQ21, A5 → AQ20, A7 → AQ13, A8 → AQ33.

| ID | Assumption | Rationale | Used in |
|---|---|---|---|
| AQ1 | Teams lose track of who does what and by when, because work is scattered across email, chat and spreadsheets. | The most common problem these platforms solve; consistent with KF-06. | Vision, OBJ-01 |
| AQ2 | Success in year one means pilot teams run their day-to-day work in Matriz, for example most of their tasks tracked there over three months. Revenue comes later. | Real, repeated use is the first meaningful signal for a new product. | OBJ-05, MVP scope |
| AQ3 | **Confirmed 2026-10-29.** Matriz is a product for other organizations. The first release goes to a small group of pilot teams. | A branded "platform" suggests a product for others. A pilot limits risk. | OBJ-05, F46 out of scope |
| AQ4 | Customers will eventually pay a recurring subscription. The MVP has no billing. | Charging is only needed once the product is proven. | F46 out of scope, NFR-10 |
| AQ5 | Target users currently rely on spreadsheets, email and chat, or find general project tools too complex. | Typical for small and medium teams. | OBJ-01 |
| AQ6 | **Confirmed 2026-10-29.** Matriz stands out through simplicity: quick to start, no training, progress visible at a glance. Previously the highest-risk assumption. | A new product can't compete on number of features. | OBJ-04, NFR-07 |
| AQ7 | **Confirmed 2026-10-29.** Users are work teams of roughly 5–50 people doing office or knowledge work. | Consistent with KF-05. This is where informal tools stop working. | User types |
| AQ8 | Each organization has one workspace containing one or several small teams. Large multi-department organizations are a later target. | Keeps permissions and structure simple. | FR-02, F08 out of scope |
| AQ9 | Users have average digital skills. Matriz must be usable without training. | Fits AQ6 and the varied backgrounds of team members. | NFR-07 |
| AQ10 | One interface language and one primary market at launch. The name suggests a Portuguese- or Spanish-speaking audience. | Keeps the first release small. | NFR-09, PD-13, F49 out of scope |
| AQ11 | **Confirmed 2026-10-29.** Four roles: Workspace Administrator, Project Manager, Team Member, Viewer. External Guests come later. | Covers set-up, planning, doing the work, and following progress. | User types, FR-04, FR-05, F07 out of scope |
| AQ12 | Daily use: Team Members see tasks due soon, update status and comment. Project Managers create and assign tasks, check progress and follow up on overdue work. | The minimum daily loop that delivers the core value. | FR-12 to FR-19 |
| AQ13 | A project is a body of work with a goal and an end date. A task is one action with one owner and a deadline. | The most widely understood business meaning. | FR-06, FR-09, FR-10 |
| AQ14 | Current pain points: unclear ownership, lost updates, no single overview. | Follows from AQ5. | OBJ-02, FR-10, FR-16 |
| AQ15 | No connections to other tools in the MVP. | Not part of the core value. | F48 out of scope |
| AQ16 | Managers mainly need each project's progress, its overdue tasks, and who is responsible for what. | Partly confirmed by KF-06. | OBJ-03, FR-18, FR-19 |
| AQ17 | **Confirmed 2026-10-29.** The top three capabilities are organizing work, assigning responsibility and due dates, and seeing progress. | Matches KF-03, KF-06 and AQ1. | MVP scope |
| AQ18 | Board, calendar and timeline views, subtasks, attachments, reminders, reports, guests and integrations can wait. | Not needed to prove the core value. | Features outside the initial scope |
| AQ19 | Matriz won't include team chat, time tracking or invoicing, file storage, or financial or budget management. File attachments on tasks (F32) are an exception and don't count as file storage (decided 2026-10-29). | Separate product categories that would weaken AQ6. | Scope limitations |
| AQ20 | The client wants help across the whole journey, starting with defining and validating the MVP. | The usual meaning of "help me develop it". | README status |
| AQ21 | The client is the final decision-maker. | No one else was mentioned. | KC-07 |
| AQ22 | Nothing exists yet. Matriz is built from scratch. | "Create" suggests something new. | README status |
| AQ23 | Matriz stores projects, tasks, comments, and members' names and email addresses. This is moderately confidential. There is no payment data and no especially sensitive personal data. | That is what task management involves. | NFR-10, SP-06 |
| AQ24 | Matriz follows strict general data-protection rules, such as the EU's GDPR. | A safe default for most markets. | NFR-01, NFR-02, SP-02, SP-05 |
| AQ25 | The pilot has tens of teams and up to a few hundred users. Growth to thousands is possible within one to three years. | Realistic for a new product. | NFR-05 |
| AQ26 | Matriz is used mainly on computers at work. Phone use comes later. Offline use is not planned. | Planning is mostly desk work for these users. | NFR-08, F50 and F51 out of scope |
| AQ27 | Reliable availability during working hours. Brief interruptions are tolerable. Loss of saved work is not. | Lost data would destroy trust. | NFR-03, NFR-04 |
| AQ28 | Common accessibility practice from the start: readable text, full use without a mouse, no reliance on colour alone. | Low cost if done early, and expected by law in some markets. | NFR-06, FR-19 |
| AQ29 | The budget is limited. | Typical for a new product; supports a small MVP. | KC-04 |
| AQ30 | First release within a few months, with no fixed external deadline. | No deadline was mentioned. | KC-05 |
| AQ31 | No existing brand material and no data to import. | Nothing existing was mentioned. | F47 out of scope |
| AQ32 | The client will need a small support team after launch. | Pilot feedback is needed to validate these assumptions. | FR-20, FR-21 |
| AQ33 | "Matriz" is the working name. | Its legal availability is unchecked. | KC-06 |

## Security and privacy requirements

Each item refers to the requirement that defines it, so the details live in one place.

| ID | Requirement | Defined in |
|---|---|---|
| SP-01 | Workspace content is only available to signed-in members. | FR-01 |
| SP-02 | Each organization can see and change only its own data. | NFR-01 |
| SP-03 | What a member can see and change is set by their role. Viewers are read-only. | FR-04, FR-05 |
| SP-04 | Deactivated members can no longer access the workspace. | FR-03 |
| SP-05 | Members can request a copy or the deletion of their personal data, under strict data-protection rules. The customer organization owns the data stored in its workspace (decided 2026-10-29). | FR-21, NFR-02 |
| SP-06 | The MVP stores no payment data. | NFR-10 |
| SP-07 | A workspace always keeps at least one Workspace Administrator. | [EC-03](user-stories.md#relevant-edge-cases-and-expected-behavior) |

## Pending technical decisions

None of these has been decided. Each must be decided in the design stage so that it meets the requirement it supports.

| ID | Decision to make | Supports |
|---|---|---|
| TD-01 | How users access Matriz on computers | NFR-08 |
| TD-02 | Where data is stored and hosted, including the geographic region | NFR-01, NFR-02 |
| TD-04 | How invitation emails are sent | FR-03 |
| TD-05 | How saved work is protected against loss (backup and recovery) | NFR-03 |
| TD-06 | How availability and capacity targets are met and monitored, once set | NFR-04, NFR-05 |
| TD-07 | How simultaneous edits to the same task are handled, once the product rule is decided | EC-07, PD-11 |
| TD-08 | How in-app notifications are delivered | FR-17 |
| TD-09 | How personal-data requests are received and fulfilled in the MVP | FR-21 |
| TD-12 | How module boundaries and the no-cycle rule are checked, for example by automated checks or code review | Architecture decision rules 1–3; raised in [architecture.md](architecture.md#pending-decisions) |

TD-03, TD-10 and TD-11 have been resolved and are recorded in the decision log. Their IDs are not reused.

## Scope limitations

- The MVP is limited to FR-01 to FR-22. Features planned for later are listed in [requirements.md](requirements.md#features-outside-the-initial-scope).
- These product categories are excluded entirely: team chat, time tracking, invoicing, file storage, and financial or budget management (AQ19). File attachments on tasks (F32, outside the initial scope) are an exception and don't count as file storage.
- The MVP is for use on computers, in one language, with no billing, no imports, and no connections to other tools (AQ4, AQ10, AQ15, AQ26, AQ31).
- The specifications only define behavior that the assumptions support. Everything else is listed as a pending product decision below.

### Pending product decisions

These need a human decision, ideally the client's. While the client is unavailable, the project decision-maker decides (KC-01). Decisions already made are in the [decision log](#decision-log).

| ID | Decision | Affects |
|---|---|---|
| PD-01 | Whether Team Members can edit, change the status of, or assign tasks they don't own. (Task creation and deletion were decided on 2026-10-29; see the decision log.) | FR-09, FR-10, FR-12, US-10, US-11 |
| PD-02 | Which roles a Project Manager can give when inviting, and whether Project Managers can deactivate members. | FR-03, FR-04, US-03 |
| PD-03 | Whether a member can hold more than one role, or whether the Workspace Administrator role includes Project Manager permissions. | FR-04, user types |
| PD-04 | Whether due date and description are required. (The title and description lengths were decided on 2026-11-12; see the decision log.) | FR-09, FR-11, US-10, EC-06 |
| PD-05 | Which statuses a project can have (F11), and what archiving a project changes (F09). | FR-06, FR-08, US-07, US-09 |
| PD-06 | Whether a Done task can move back to an earlier status, and whether Done tasks appear in "My tasks". | FR-12, FR-14, US-11, US-13 |
| PD-07 | Whether progress shows a percentage as well as the count, and the rounding rule. | FR-18, US-17 |
| PD-08 | Which time zone decides "today" for overdue marking, and where tasks without a due date are ordered. | FR-14, FR-19, US-13, US-18, EC-06 |
| PD-09 | Whether invitations can be resent, whether they expire, and how pending invitations are shown. | FR-03, EC-02 |
| PD-10 | What happens to tasks owned by deactivated members or by people removed from a project. (How a former project owner is shown in the project form was decided on 2026-11-12; see the decision log.) | FR-03, FR-07, EC-04, EC-09 |
| PD-11 | Which change wins when two people edit the same task at the same time. | FR-12, EC-07 |
| PD-12 | Whether members are notified of their own actions, for example assigning a task to themselves. | FR-17, US-16 |
| PD-13 | The launch language for the whole interface. (Task status labels are shown in Portuguese, decided 2026-11-12; see the decision log.) | NFR-09 |
| PD-15 | Numeric targets for data-request response time, availability, capacity, accessibility conformance and learnability. | NFR-02, NFR-04, NFR-05, NFR-06, NFR-07 |
| PD-16 | Whether one account can be a member of more than one workspace, and whether the person who creates a workspace automatically becomes its Workspace Administrator. | FR-01, FR-02, FR-14, NFR-01 |
| PD-17 | Whether a Viewer who owns a task can change its status or comment on it. FR-05 currently forbids both. | FR-05, FR-10, FR-12, FR-16, US-19 |
| PD-18 | Whether the rule "a member can delete what they created, not what others created" also sets what a personal-data deletion request removes, and what happens when the only Workspace Administrator asks for their data to be deleted. | FR-09, FR-21, EC-03, SP-07 |
| PD-19 | How the identity of someone making a personal-data request is verified, and which workspace data support staff may access when handling it. | FR-21, NFR-02, SP-05, TD-09 |
| PD-20 | Whether a member must belong to a project to create a task in it. FR-07 and FR-09 do not say. | FR-07, FR-09, US-10 |
| PD-22 | Whether notifications can be marked as read, which would allow an unread count. FR-17 does not say. | FR-17, US-16 |
| PD-23 | Whether feedback has a fixed list of categories. FR-20 gives "a problem report or a suggestion" only as examples. | FR-20, US-20 |
| PD-24 | What a signed-in person who belongs to no workspace sees. | FR-01, FR-02, PD-16 |

PD-14 has been resolved and is recorded in the decision log. Its ID is not reused.

### Decision log

Decisions made by a person, recorded under AGENTS.md D2. Approvers are recorded by role.

| Date | Decision | Approved by | Resolves | Updated in |
|---|---|---|---|---|
| 2026-10-29 | The client endorsed the project description, so KF-05 and KF-06 are facts. | Project decision-maker | — | requirements.md (Known facts) |
| 2026-10-29 | While the client is unavailable, the project decision-maker makes pending decisions and answers agents' questions. | Project decision-maker | — | KC-01, KC-07, AGENTS.md (S4, W2) |
| 2026-10-29 | AQ3, AQ6, AQ7, AQ11 and AQ17 are confirmed. | Project decision-maker | PD-14 | Assumptions table, OBJ-04, OBJ-05, NFR-07, User types, Vision, MVP scope, README status |
| 2026-10-29 | Any user can create a workspace. Each account belongs to one person only. | Project decision-maker | — | FR-01, FR-02 |
| 2026-10-29 | Viewers can be project members and task owners. | Project decision-maker | — | FR-07, FR-10 |
| 2026-10-29 | Team Members can create tasks, and can see projects they are not members of. | Project decision-maker | PD-01 (in part) | FR-08, FR-09 |
| 2026-10-29 | A member can delete the tasks they created, but not tasks created by others. | Project decision-maker | PD-01 (in part) | FR-09 |
| 2026-10-29 | The customer organization owns the data stored in its workspace. | Project decision-maker | — | SP-05 |
| 2026-10-29 | File attachments on tasks (F32) are an exception to the "no file storage" exclusion. | Project decision-maker | — | AQ19, Scope limitations |
| 2026-11-05 | The MVP is built as a modular monolith with the components CMP-01 to CMP-13. No technology is chosen by this decision. | Project decision-maker | — | architecture.md |
| 2026-11-05 | The dashboard combines existing MVP views only and is not a new feature; F41 Manager overview stays outside the initial scope. | Project decision-maker | — | docs/ui/README.md (UI-A6) |
| 2026-11-12 | The MVP uses Java 21 and Spring Boot 3, a static HTML/CSS/JS front end and PostgreSQL. | Project decision-maker | TD-10, TD-11 | architecture.md (Technology stack) |
| 2026-11-12 | Sign-in uses email and password, with passwords stored only as BCrypt hashes, and a server-side session cookie (HttpOnly, SameSite=Lax) with CSRF protection and an 8-hour idle timeout. | Project decision-maker | TD-03 | database.md (Account, DA-08), openapi.yaml, docs/implementation-plan.md (INC-02) |
| 2026-11-12 | Project Managers can delete a project after confirming. The deletion cannot be undone and also deletes the project's tasks with their comments and notifications, and its project members. | Project decision-maker | — | FR-06, US-27, database.md (R-07, R-09, BR-21), openapi.yaml (deleteProject), migration V2 |
| 2026-11-12 | Searching the project list by part of the project name, ignoring case, is in the MVP. All other search (F27) stays outside the initial scope. | Project decision-maker | — | FR-08, F27, US-28, openapi.yaml (listProjects) |
| 2026-11-12 | The project list is paged with a page number and a page size (default 20, at most 100). | Project decision-maker | — | FR-08, AC-28.3, openapi.yaml (listProjects) |
| 2026-11-12 | A project's owner is its creator unless the Project Manager chooses another active member of the workspace. (Leaving a project without an owner relied on the assumption DA-03 in database.md; superseded on 2026-11-12, when a project was decided to always have an owner.) | Project decision-maker | — | FR-06, openapi.yaml (createProject) |
| 2026-11-12 | A project's name is 3 to 100 characters long and its description at most 1000 characters. | Project decision-maker | — | FR-06, database.md (Project, BR-21), openapi.yaml |
| 2026-11-12 | Any Project Manager of the workspace can edit or delete its projects, and the creator of a project becomes one of its project members. | Project decision-maker | — | FR-06, FR-07, database.md (BR-21), openapi.yaml |
| 2026-11-12 | The demo data includes a Project Manager who belongs only to Northwind Lab (Dan Demo), to show that workspaces are kept apart. | Project decision-maker | — | README (demo users), DemoDataSeeder |
| 2026-11-12 | A task title is trimmed and must be 3 to 150 characters long, so a title of only spaces is refused. A task description is at most 2000 characters. | Project decision-maker | PD-21; PD-04 (lengths only) | FR-09, US-10 (AC-10.5), US-25 (AC-25.2), database.md (Task, BR-10), openapi.yaml |
| 2026-11-12 | Task priority (F17) stays outside the initial scope. | Project decision-maker | — | docs/implementation-plan.md (INC-04) |
| 2026-11-12 | A status may be sent when a task is created; it is To do when not sent. The API values stay `To do`, `In progress`, `Done`. | Project decision-maker | — | openapi.yaml (createTask) |
| 2026-11-12 | Task statuses are shown in Portuguese (Por fazer, Em curso, Concluída); the API keeps its values. The rest of the interface stays in English until PD-13 is decided. | Project decision-maker | PD-13 (in part) | static/js/app.js (one mapping) |
| 2026-11-12 | The project task list can be filtered by status and owner, combined, and is paged like the project list. Earliest due date first, tasks without a due date last, then by title. Filtering by due date is left for later. | Project decision-maker | — | FR-13, FR-15, openapi.yaml (listTasks) |
| 2026-11-12 | Provisional task permissions until PD-01, PD-03 and PD-17 are decided: Project Managers of the workspace and Team Members who own a task edit its title, description and due date; only Project Managers change the owner; only the owner changes the status; only the creator deletes; Viewers and Workspace Administrators only read tasks. | Project decision-maker | — | openapi.yaml (updateTask, deleteTask), AccessPolicy |
| 2026-11-12 | Task operations on one task use the project in the path (`/projects/{project_id}/tasks/{task_id}`); a task used with another project's id is not found. The task form offers workspace members as owners, and the backend refuses those outside the project. | Project decision-maker | — | openapi.yaml, docs/implementation-plan.md (INC-04) |
| 2026-11-12 | Tasks can have several assignees besides their single owner (FR-10 unchanged). Eligible: active members of the task's project, Viewers included. Only Project Managers assign and remove (provisional until PD-01). Being assigned adds nothing to "My tasks", notifications or status rights. A duplicate assignment is refused with 409 and prevented by a primary key. | Project decision-maker | — | FR-22, US-29, database.md (TaskAssignee, R-19, R-20, BR-22), openapi.yaml 0.5.0-draft, migration V3, constraints.md (Scope limitations) |
| 2026-11-12 | A comment is trimmed and must be 1 to 2000 characters, so a comment of only spaces is refused. Author and date are set by the server; a client that sends them is refused (422). Comments are listed oldest first, without pages, and cannot be edited or deleted in the MVP. | Project decision-maker | — | FR-16, US-15 (AC-15.1 to AC-15.4), database.md (BR-13), openapi.yaml 0.6.0-draft |
| 2026-11-12 | Provisional comment permissions until PD-03, PD-17 and PD-20 are decided: Project Managers and Team Members who are members of the task's project may comment; Viewers and Workspace Administrators may not; every workspace member may read comments. The "comment added" notification (FR-17) is left for the notifications increment. | Project decision-maker | — | openapi.yaml (addTaskComment), AccessPolicy |
| 2026-11-12 | The user interface learns the API's base URL from the setting `MATRIZ_API_BASE_URL` (property `matriz.api-base-url`), served as `GET /config.js` and read only by the interface. Empty, the default, means the same server; otherwise only a path on the same site is accepted, such as `/api`, and any other value stops startup, because the session and CSRF cookies are same-site only (TD-03). `GET /config.js` is open without a session; it holds no secret and no workspace content (SP-01). | Project decision-maker | — | README (environment, User interface), openapi.yaml (description), docs/implementation-plan.md (INC-06), SecurityConfig, ClientConfigController |
| 2026-11-12 | After a failed sign-in the email is kept and the password is cleared; this is a deliberate exception to keeping what the user typed after a failed submission. | Project decision-maker | — | README (User interface), docs/implementation-plan.md (INC-06), static/js/app.js |
| 2026-11-12 | UI-A7 is approved: after an ended session, the screen the member was on is reopened only right after signing in, and only if the same account signs in; otherwise the member goes to the first screen (UI-A1, currently the project list until the dashboard exists). A member who opens sign-in while already signed in always goes to the first screen. Signing out forgets the earlier screen. | Project decision-maker | — | docs/ui/README.md (UI-A7), README (User interface), docs/implementation-plan.md (INC-06) |
| 2026-11-12 | Each project row on the dashboard also shows that project's task counts by status, from the project task list (`listTasks` `status_counts`). No total across projects is shown, so the dashboard keeps combining existing views (UI-A6; F41 stays outside the initial scope). | Project decision-maker | — | docs/ui/README.md (E-12), README (User interface), docs/implementation-plan.md (INC-06), static/js/app.js |
| 2026-11-12 | The dashboard leaves out My tasks (FR-14), notifications (FR-17), members (FR-03, FR-04), feedback (FR-20) and personal-data requests (FR-21) until they have API operations. | Project decision-maker | — | docs/ui/README.md (Screen elements), README (User interface), docs/implementation-plan.md (INC-06) |
| 2026-11-12 | E-01 shows the names of every workspace the member belongs to, as text, without a workspace switcher (PD-16 stays pending). The dashboard's Projects section keeps a Workspace column. | Project decision-maker | — | docs/ui/README.md (E-01, E-12), README (User interface), static/js/app.js |
| 2026-11-12 | UI-A1 is confirmed: the dashboard (`#/dashboard`) is the first screen after sign-in, for a signed-in member who opens sign-in, and for unknown addresses. It replaces "the project list until the dashboard exists" in the UI-A7 decision. | Project decision-maker | UI-A1 | docs/ui/README.md (UI-A1, UI-A7), README (User interface), docs/implementation-plan.md (AC-INC06.7) |
| 2026-11-12 | The dashboard's Projects section shows the first 10 projects with the total from the API, "Showing N of total" and "See all projects" in a footer under the table. | Project decision-maker | UI-A2 (Projects section only) | docs/ui/README.md (UI-A2, E-13), README (User interface), static/js/app.js |
| 2026-11-12 | The demo data includes the workspace Orchard Office, without projects, with Eva Demo (Project Manager) and Finn Demo (Workspace Administrator), to show the empty dashboard with and without "+ New project". | Project decision-maker | — | README (Demo users), DemoDataSeeder |
| 2026-11-12 | "+ New project" (E-14) stays hidden for Workspace Administrators while PD-03 is pending (provisional). On an empty project list it appears once, in the empty state (wireframe Variant B), on the dashboard and the Projects screen. | Project decision-maker | — | docs/ui/README.md (E-14), README (User interface), static/js/app.js |
| 2026-11-12 | UI-A4 is confirmed for the dashboard's section loading and error states: "Loading…" (E-19), and "This section could not be loaded." with a "Try again" button that reloads that section only (E-20). | Project decision-maker | UI-A4 (E-19 and E-20 only) | docs/ui/README.md (UI-A4) |
| 2026-11-12 | KC-03 now says that development is under way instead of "No code has been written yet". | Project decision-maker | — | constraints.md (KC-03) |
| 2026-11-12 | INC-06 screen 3 covers the project list and the create and edit project form; the project form moves to the shared form parts. | Project decision-maker | — | docs/implementation-plan.md (INC-06) |
| 2026-11-12 | The Projects screen keeps "Loading projects…" and its current error state with Try again. UI-A4's dashboard wording and single-load guard are not applied there. | Project decision-maker | — | docs/ui/README.md (UI-A4, Projects screen), README (User interface), docs/implementation-plan.md (INC-06), static/js/app.js |
| 2026-11-12 | Each project row on the Projects screen also shows that project's task counts by status, from the project task list (`listTasks` `status_counts`), as the dashboard does. No total across projects is shown (UI-A6; F41 stays outside the initial scope). | Project decision-maker | — | docs/ui/README.md (Projects screen), README (User interface), docs/implementation-plan.md (INC-06), static/js/app.js |
| 2026-11-12 | A Projects page past the last opens the last page that has projects, keeping the search. | Project decision-maker | — | README (User interface), docs/implementation-plan.md (AC-INC06.28), static/js/app.js |
| 2026-11-12 | The Projects screen shows 20 projects per page, the `listProjects` default; the dashboard keeps 10. | Project decision-maker | — | README (User interface), docs/implementation-plan.md (INC-06), static/js/app.js |
| 2026-11-12 | A project's start date must not be after its due date when both are given; creating or editing a project that breaks this is refused with 422. Migration V4 also enforces it with a database check; the project decision-maker sets correct dates for existing projects that break it. Built in INC-07 on 2026-11-16 (database check `project_date_order_ck`). | Project decision-maker | — | FR-06, database.md (Project, BR-21), openapi.yaml 0.7.0-draft (createProject, updateProject, ProjectValidationFailed), migration V4, docs/implementation-plan.md (INC-07) |
| 2026-11-12 | A project always has an owner: its creator unless another active member of the workspace is chosen. "No owner" is removed from the project form and an empty owner (`null`) is refused. Existing projects without an owner get an owner named by the project decision-maker, and migration V4 makes the owner required; while any such project remains, its other fields may be saved with the owner left empty. Description, start date and due date stay optional (DA-03, unconfirmed). Built in INC-07 on 2026-11-16, except removing "No owner" from the project form, which is part of INC-06 part 3b. | Project decision-maker | DA-03 (owner part, rejected) | FR-06, database.md (Project, R-06, BR-23, DA-03), database-diagram.md (R-06, Project), docs/ui/dashboard-wireframe.html (Projects note and sample rows), openapi.yaml 0.7.0-draft (createProject, updateProject, ProjectSummary), migration V4, docs/implementation-plan.md (INC-07) |
| 2026-11-12 | In the project form, an owner who is no longer an active member is shown as "Former member", without a name, and is kept unless someone else is chosen. The project list, project detail and dashboard keep showing a deactivated owner's name until the rest of PD-10 is decided. Not built yet. | Project decision-maker | PD-10 (in part) | constraints.md (PD-10), docs/ui/README.md (PD-10) |
| 2026-11-12 | The API tests stand as the evidence for the role-specific checks of AC-INC06.23, AC-INC06.24, AC-INC06.25 and AC-INC06.30, instead of signing in with each role in the browser. | Project decision-maker | — | docs/implementation-plan.md (INC-06) |
| 2026-11-16 | The database agent's SQL check of migration V4 on scratch databases (a project without an owner or with its start date after its due date is refused; a failed precondition rolls V4 back; V4 applies after the fix) stands as the evidence for AC-INC07.8, instead of an automated test. | Project decision-maker | — | docs/implementation-plan.md (INC-07) |
| 2026-11-16 | `mvn test` on the test database (124 tests, 0 failures) stands as the evidence for AC-INC07.10, instead of `mvn verify`. INC-07 is marked Done while a refused `PATCH` on the running build and V4 on the development database after a restart remain Not verified. | Project decision-maker | — | docs/implementation-plan.md (INC-07) |
