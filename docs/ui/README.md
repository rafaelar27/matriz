# Matriz — UI wireframes

> **Status:** draft, written on 2026-11-05. Low-fidelity design artifacts only: grayscale structure, placeholder text, no branding and no final styling. The front end is static HTML, CSS and JavaScript ([Technology stack](../architecture.md#technology-stack), 2026-11-12). These wireframes do not depend on it. Requirement IDs are defined in [requirements.md](../requirements.md), user stories in [user-stories.md](../user-stories.md), data in [database.md](../database.md) and pending decisions in [constraints.md](../constraints.md#pending-product-decisions). Assumptions are labelled **UI-Ax** and must be confirmed before they are treated as rules.

## Files

| File | Contents |
|---|---|
| [dashboard-wireframe.html](dashboard-wireframe.html) | Self-contained wireframe of the dashboard, opened in any web browser. Variant A: Project Manager with data. Variant B: empty state for a new workspace. Variant C: Viewer. Section states: loading and error. |

## Dashboard

### Screen name and purpose

**Dashboard**: the first screen a signed-in member sees (assumption **UI-A1**).

It shows each member, in one place, **what they need to do, how every project is progressing and what has happened that concerns them**. That is the MVP's core value. It is built only from three existing MVP views and adds no summary, chart or statistic of its own, because F41 Manager overview is outside the initial scope:

| Dashboard section | Existing MVP view | FR | US |
|---|---|---|---|
| My tasks | "My tasks" | FR-14, FR-19 | US-13, US-18 |
| Projects | Project list with progress | FR-08, FR-18 | US-09, US-17, US-19 |
| Recent notifications | In-app notifications | FR-17 | US-16 |

From the dashboard the member can open their tasks, the projects, the members list and their notifications, create a project (Project Managers only, FR-06, US-06), send feedback (FR-20, US-20), request their personal data (FR-21, US-21) and sign out (FR-01, CMP-01).

### Screen elements

The IDs E-01 to E-20 are the labels shown on the wireframe; E-21 is not on it (added 2026-11-19). Data names follow [database.md](../database.md). "Derived" means calculated, not stored (BR-15, BR-16). "All" means all four user types: Workspace Administrator, Project Manager, Team Member and Viewer.

| ID | Element | Purpose | Related FR/US | Data shown (entity.attribute) | Visible to (user type) |
|---|---|---|---|---|---|
| E-01 | Workspace name (top bar) | **Withdrawn on 2026-11-19** by the project decision-maker: the header no longer shows workspace names. Before, it showed the names of every workspace the member belongs to, as text, with no workspace switcher (PD-16). The ID is not reused (D4); the wireframe still shows it. | FR-02, NFR-01; US-02 | Workspace.name | All |
| E-02 | Main menu: Dashboard | Returns to this screen; marked as current. | FR-01; US-01 | — | All |
| E-03 | Main menu: Projects | Opens the full project list. | FR-08; US-09, US-19 | — | All |
| E-04 | Main menu: My tasks | Opens the full "My tasks" list. | FR-14; US-13 | — | All |
| E-05 | Main menu: Members | Opens the workspace member list. The Invite action there is shown only to Workspace Administrators and Project Managers (AC-03.4). | FR-03, FR-04; US-03, US-04, US-05 | — | All (actions on the target screen vary by role) |
| E-06 | Main menu: Notifications | Opens all of the member's notifications. No unread count (UI-A5). | FR-17; US-16 | — | All |
| E-07 | Account menu | Shows who is signed in and offers Send feedback, Request my personal data and Sign out. No profile page: F06 is out of scope. | FR-01, FR-20, FR-21; US-20, US-21 | Account.name | All |
| E-08 | Page heading | Names the screen. | — | — | All |
| E-09 | My tasks section | The signed-in member's tasks across projects, earliest due date first, with an "Overdue" text label. A row opens the task (UI-A3). | FR-14, FR-19, NFR-06; US-13, US-18; AC-13.1, AC-13.2, AC-18.1–18.4; EC-06 | Task.title, Project.name, Task.status, Task.due_date; overdue (derived, BR-16) | All (a Viewer who is a project member can own tasks, FR-10) |
| E-10 | "See all my tasks" link | Opens the full "My tasks" list when the section shows only the first items (UI-A2). | FR-14; US-13 | — | All |
| E-11 | My tasks empty state | Message "No tasks are assigned to you." | FR-14; AC-13.3 | — | All |
| E-12 | Projects section | The first projects of the member's workspaces (UI-A2) with workspace, owner, due date, progress "N of M tasks done" and the project's task counts by status, from the project task list. No total across projects (UI-A6). A row opens the project task list. | FR-08, FR-18, FR-13; US-09, US-17, US-19; AC-17.1, AC-19.1, AC-19.3; EC-05 | Project.name, Workspace.name, Account.name (through Project.owner_member_id → Member.account_id), Project.due_date; progress (derived, BR-15); task counts per Task.status (derived, FR-13) | All |
| E-13 | "See all projects" link | Opens the full project list (UI-A2). Shown under the table with "Showing N of total" (decided 2026-11-12; the wireframe shows it in the heading). | FR-08; US-09 | — | All |
| E-14 | "+ New project" button | Starts project creation. | FR-06; US-06 | — | Project Manager and Workspace Administrator (decided 2026-11-19, PD-03 in part). Hidden for Team Member and Viewer (FR-05, AC-19.2). On an empty list it appears once, in the empty state (E-15). |
| E-15 | Projects empty state | Message "No projects yet."; Project Managers and Workspace Administrators also see E-14. | FR-08 | — | All |
| E-16 | Recent notifications section | The member's latest notifications, newest first. An item opens the task concerned. | FR-17; US-16 | Notification.type, Task.title (through Notification.task_id), Account.name of the comment author (through Notification.comment_id → Comment.author_member_id, only for "comment added"), Notification.created_at | All (every member can receive notifications) |
| E-17 | "See all" notifications link | Opens all notifications (UI-A2). | FR-17; US-16 | — | All |
| E-18 | Notifications empty state | Message "No notifications." (UI-A4). | FR-17 | — | All |
| E-19 | Section loading state | Placeholder rows and the text "Loading…" while a section loads (UI-A4). | NFR-06, NFR-07 | — | All |
| E-20 | Section error state | Message "This section could not be loaded." and a "Try again" button (UI-A4). | NFR-03, NFR-07 | — | All |
| E-21 | Main menu: New workspace | Opens the New workspace form (name only). Shown on every signed-in screen. | FR-02; US-02 | — | All |

**Not built yet** (decided 2026-11-12, because these have no API operations yet): E-04 to E-06, E-09 to E-11, E-16 to E-18, and Send feedback and Request my personal data in E-07 (FR-03, FR-04, FR-14, FR-17, FR-20, FR-21).

**Differences by user type**

| User type | Differences on the dashboard |
|---|---|
| Workspace Administrator | Sees "+ New project" (E-14, also in the empty state E-15) and can create, edit and delete projects like a Project Manager (decided 2026-11-19, PD-03 in part). On tasks (decided 2026-11-19, PD-03 in part), in projects they are members of, a Workspace Administrator creates tasks and comments; she also assigns and removes assignees and changes a task's owner, changes the status of tasks she owns and deletes tasks she created. She is not offered editing of a task's title, description or due date (pending PD-03). Otherwise as Team Member until the rest of PD-03 is decided. Inviting and managing members happens on the Members screen (E-05). |
| Project Manager | Sees "+ New project" (E-14), also in the empty state (E-15). |
| Team Member | No "+ New project". Sees every project, including those they are not a member of (FR-08). |
| Viewer | No "+ New project" and no other create or change action (FR-05, AC-19.2). Same project list and the same progress figures as everyone else (AC-19.1, AC-19.3). Tasks open read-only (AC-11.3); PD-17 may change this for tasks the Viewer owns. |

### Navigation

| From | Leads to | MVP basis |
|---|---|---|
| E-02 Dashboard | This screen | UI-A1 |
| E-03 Projects, E-13 See all projects | Project list screen | FR-08, US-09 |
| E-04 My tasks, E-10 See all my tasks | "My tasks" screen | FR-14, US-13 |
| E-05 Members | Member list screen (invite, change role and deactivate there, by role) | FR-03, FR-04, US-03–US-05 |
| E-06 Notifications, E-17 See all | Notifications screen | FR-17, US-16 |
| A task row in E-09 | Task detail: status, owner, due date, description, comments | FR-09–FR-12, FR-16; US-11, US-15 (UI-A3) |
| A project row in E-12 | Project task list, with filters | FR-13, FR-15; US-12, US-14 |
| E-14 + New project | Create-project form (name, description, owner, start date, due date) | FR-06, US-06 |
| A notification in E-16 | Task detail of the task concerned | FR-17, US-16 (UI-A3) |
| E-07 Send feedback | Feedback form | FR-20, US-20 |
| E-07 Request my personal data | Personal-data request (copy or deletion) | FR-21, US-21 |
| E-07 Sign out | Sign-in screen | FR-01, CMP-01 |
| E-20 Try again | Reloads that section only | UI-A4 |
| E-21 New workspace | New-workspace form (name); after creating, the dashboard | FR-02, US-02 |

```text
Sign in ──► Dashboard (UI-A1)
             ├── Projects ──► Project task list ──► Task detail
             │      └── + New project (Project Manager) ──► Create-project form
             ├── My tasks ──► Task detail
             ├── Notifications ──► Task detail
             ├── Members ──► Member list (invite / role / deactivate, by role)
             └── Account menu ──► Send feedback · Request my personal data · Sign out
```

### Wireframe

- **File:** [dashboard-wireframe.html](dashboard-wireframe.html). Open it in any web browser; it needs no network connection.
- **Variants:** A, Project Manager with data (including an overdue task, a task with no due date and a project with "0 of 0 tasks done"); B, empty state for a new workspace (AC-13.3, EC-05); C, Viewer; plus section loading and error states.
- **Example data** is fictional. "Today" in the examples is 2026-11-05.

### Assumptions

These are not confirmed requirements.

| ID | Assumption | Why |
|---|---|---|
| UI-A1 | **Confirmed 2026-11-12.** The dashboard is the first screen after sign-in; it is also shown to a signed-in member who opens sign-in and for unknown addresses. | The task asks for it; no document says which screen follows sign-in (FR-01, US-01). Confirmed by the project decision-maker on 2026-11-12 (see the [decision log](../constraints.md#decision-log)). |
| UI-A2 | Each section shows only the first few items, with a "See all" link. The Projects section shows the first 10 projects with the total from the API (decided 2026-11-12); the number for the other sections is not set. | Keeps the screen simple (OBJ-04); no requirement sets a limit. |
| UI-A3 | Clicking a task or a notification opens the task detail. Status is not changed on the dashboard itself. | Keeps the status change in the task detail, where AC-11.1 to AC-11.3 apply. |
| UI-A4 | Loading, error and "No notifications." states and their wording. Confirmed 2026-11-12 for E-19 and E-20. Not applied on the Projects screen, which keeps "Loading projects…" and its current error state with Try again (decided 2026-11-12). | No edge case covers them. |
| UI-A5 | Notifications show no unread count or "new" marker. | Read state is not defined (PD-22). |
| UI-A6 | The dashboard combines existing MVP views only and is not a new feature. | F41 Manager overview is out of scope; approved by the project decision-maker on 2026-11-05 (see the [decision log](../constraints.md#decision-log)). |
| UI-A7 | After an ended session (401), the screen the member was on is reopened only right after signing in, and only if the same account signs in; another account goes to the first screen, the dashboard (UI-A1). After a full page reload the earlier account is not known, so the screen is kept and the API still decides access. A member who opens the sign-in screen while already signed in always goes to the first screen. Signing out forgets the earlier screen. | No document says where sign-in leads after a session ends (FR-01, US-01). Approved by the project decision-maker on 2026-11-12 (see the [decision log](../constraints.md#decision-log)). |

### Open questions and pending decisions

| ID | Effect on the dashboard |
|---|---|
| PD-03 | Whether a Workspace Administrator sees "+ New project" (E-14) was decided on 2026-11-19: yes (see the [decision log](../constraints.md#decision-log)). Still pending: whether a member can hold more than one role, and which other Project Manager rights (editing a task's title, description and due date, project members, archiving) a Workspace Administrator has; her task actions (create, comment, assign, owner, own status, delete own) were decided on 2026-11-19. |
| PD-05 | Whether archived projects appear in the Projects section (E-12), and whether a project status is shown. |
| PD-06 | Whether Done tasks appear in My tasks (E-09). |
| PD-07 | Whether progress also shows a percentage. |
| PD-08 | Where tasks without a due date are placed in My tasks, and which time zone decides "today" for the overdue label. |
| PD-10 | How the owner of a task or project is shown when that member has been deactivated. Decided in part on 2026-11-12 (see the [decision log](../constraints.md#decision-log)): in the project form, an owner who is no longer an active member is shown as "Former member", without a name, and is kept unless someone else is chosen. The project list, project detail and dashboard keep showing a deactivated owner's name until the rest of PD-10 is decided. Built on 2026-11-16 (INC-06 part 3b). |
| PD-13 | The launch language for all labels. Labels are in English here as placeholders. |
| PD-16 | Whether a workspace switcher is needed in the top bar (E-01, withdrawn on 2026-11-19; the header shows no workspace names). |
| PD-17 | Whether a Viewer who owns a task can change its status from the task detail. |
| PD-22 | Whether notifications can be marked as read, which would allow an unread count (UI-A5). |
| PD-24 | What a signed-in person who belongs to no workspace sees instead of the dashboard (FR-02, PD-16). |

## Projects screen

The full project list, opened by E-03 and E-13 (FR-08, US-09). It has no wireframe. Decided on 2026-11-12 (see the [decision log](../constraints.md#decision-log)):

- The Projects screen keeps "Loading projects…" and its current error state with Try again. UI-A4's dashboard wording and single-load guard are not applied there.
- Each project row also shows that project's task counts by status, from the project task list (`listTasks` `status_counts`), as the dashboard does. No total across projects is shown (UI-A6; F41 stays outside the initial scope).

Not decided yet: whether Try again may send a second request on this screen.
