# Matriz — Requirements

> **Status:** draft, pending client validation. Only the known facts (KF-01 to KF-06), the assumptions marked Confirmed and the decisions in the [decision log](constraints.md#decision-log) are confirmed. Everything else rests on assumptions (AQ1–AQ33), which are listed with their rationale in [constraints.md](constraints.md#assumptions-made). User stories and acceptance criteria are in [user-stories.md](user-stories.md).

## Vision and objectives of the application

### Vision

Matriz is a project and task management platform for work teams. A team can see in one place **what needs to be done, who is responsible, by when, and how far each project has progressed**. That replaces work scattered across email, chat and spreadsheets. *(The core value statement is derived from assumption AQ1 and confirmed AQ17.)*

### Known facts

| ID | Fact | Source |
|---|---|---|
| KF-01 | The product is called Matriz. | Client description |
| KF-02 | It is described as a platform. | Client description |
| KF-03 | Its purpose is project and task management. | Client description, project description |
| KF-04 | The client wants to create it and has asked for help developing it. | Client description |
| KF-05 | It is designed for work teams. | Project description, endorsed by the client (confirmed 2026-10-29) |
| KF-06 | It includes progress tracking. | Project description, endorsed by the client (confirmed 2026-10-29) |

### Objectives

| ID | Objective | Basis |
|---|---|---|
| OBJ-01 | Give teams one place to organize their work instead of email, chat and spreadsheets. | Assumptions AQ1, AQ5 |
| OBJ-02 | Make the responsible person and the deadline explicit for every task. | Assumptions AQ14, AQ17 |
| OBJ-03 | Show managers and stakeholders the progress and delays of each project. | KF-06, assumption AQ16 |
| OBJ-04 | Stand out through simplicity, so that Matriz is usable without training. | AQ6 (confirmed), assumption AQ9 |
| OBJ-05 | Validate Matriz with pilot teams before a wider launch. Success means most pilot teams still use it for their real projects after three months. | Assumption AQ2, AQ3 (confirmed) |

## User types

| User type | Goals | Responsibilities | Main needs |
|---|---|---|---|
| **Workspace Administrator** | Get the team onto Matriz and keep access under control | Creates the organization's workspace, invites and deactivates members, assigns roles | Simple setup, easy member management, confidence that only the right people have access |
| **Project Manager** | Deliver projects on time and know where things stand | Creates projects, breaks work into tasks, assigns owners and due dates, follows up on delays | A clear overview of progress, quick spotting of overdue work, an easy way to assign and adjust tasks |
| **Team Member** | Know what to do and by when, and report progress without friction | Completes assigned tasks, updates their status, communicates through comments | A personal list of their tasks, clear due dates, notifications about work that concerns them |
| **Viewer** | Follow progress without managing the work, for example a department head | Looks at projects and tasks without changing them | Visibility of progress and status without needing to ask the team |

- The four user types come from AQ11, confirmed on 2026-10-29. The target users (work teams of roughly 5–50 people) come from AQ7, also confirmed.
- Whether one member can hold more than one role is pending decision [PD-03](constraints.md#pending-product-decisions).
- A fifth user type, the **External Guest** (someone outside the organization invited to one project), is outside the initial scope. See F07 under [Features outside the initial scope](#features-outside-the-initial-scope).

## Functional requirements

Priorities use MoSCoW: **Must have / Should have / Could have / Won't have (for now)**. "Source feature" refers to the feature IDs (F01–F51) from the functional proposal. These IDs are kept so that earlier and later work can be compared. Each requirement's user stories are listed in the [traceability table](user-stories.md#traceability).

| ID | Requirement | User type(s) | Source feature | Priority |
|---|---|---|---|---|
| FR-01 | Users can create a personal account and sign in. Each account belongs to one person only. Workspace content is only available to signed-in members. | All user types | F01 Sign up and sign in | Must have |
| FR-02 | Any user with an account can create a workspace for an organization. Each workspace holds its own projects and members. | Workspace Administrator | F02 Workspace creation | Must have |
| FR-03 | Workspace Administrators and Project Managers can invite people to the workspace by email address. Members who leave can be deactivated, after which they can no longer access the workspace. Workspace Administrators can deactivate members; whether Project Managers can also do so is pending decision [PD-02](constraints.md#pending-product-decisions). | Workspace Administrator, Project Manager | F03 Invite and remove members | Must have |
| FR-04 | Each member holds one of four roles: Workspace Administrator, Project Manager, Team Member or Viewer. The role determines what the member can see and change. Workspace Administrators assign and change roles. | Workspace Administrator | F04 Roles and permissions | Must have |
| FR-05 | A Viewer can see all of the workspace's projects and tasks. A Viewer cannot create, edit, delete or archive projects or tasks, change a task's status or owner, or add comments. | Viewer | F05 Read-only access | Should have |
| FR-06 | A Project Manager can create a project with a name, description, owner, start date and due date, edit it, and archive it when finished. What archiving changes is pending decision [PD-05](constraints.md#pending-product-decisions). | Project Manager | F09 Create, edit and archive projects | Must have |
| FR-07 | A Project Manager chooses which workspace members take part in a project. Any workspace member, including a Viewer, can be a project member. Only project members can be assigned the project's tasks. | Project Manager | F10 Project members | Must have |
| FR-08 | A project list shows every project in the workspace with its owner and due date. Team Members can see every project in the workspace, including projects they are not members of. Whether the list also shows a project status, and which statuses exist, is pending decision [PD-05](constraints.md#pending-product-decisions). | Project Manager, Team Member, Viewer, Workspace Administrator | F11 Project list | Must have |
| FR-09 | Tasks can be created, edited and deleted within a project. Project Managers and Team Members can create tasks. A member can delete the tasks they created, but not tasks created by others. Each task has a title, which is required, and a description. Who may edit which tasks is pending decision [PD-01](constraints.md#pending-product-decisions). | Project Manager, Team Member | F13 Create, edit and delete tasks | Must have |
| FR-10 | Each task has exactly one owner, who must be a member of the task's project. A Viewer who is a project member can be a task owner. | Project Manager | F14 Assign a responsible person | Must have |
| FR-11 | A task can have a due date. Whether a due date is required is pending decision [PD-04](constraints.md#pending-product-decisions). | Project Manager, Team Member | F15 Due dates | Must have |
| FR-12 | Each task has exactly one of three statuses: To do, In progress or Done. A new task starts as To do. | Team Member, Project Manager | F16 Task status | Must have |
| FR-13 | A project task list shows all tasks in the project with their owner, status and due date. | Project Manager, Team Member, Viewer | F24 Project task list | Must have |
| FR-14 | "My tasks" lists all tasks owned by the signed-in member across projects, ordered by due date with the earliest first. | Team Member | F25 "My tasks" | Must have |
| FR-15 | Task lists can be filtered by owner, status or due date. | Project Manager, Team Member, Viewer | F26 Filter tasks | Should have |
| FR-16 | Members can comment on tasks. Empty comments, or comments containing only spaces, are not posted. | Team Member, Project Manager | F31 Task comments | Must have |
| FR-17 | A member receives a notification inside Matriz when they are assigned a task, and when someone comments on a task they own. | Team Member, Project Manager | F36 In-app notifications | Must have |
| FR-18 | Each project shows its progress as the number of Done tasks out of all its tasks, for example "2 of 8 tasks done". | Project Manager, Viewer | F39 Project progress indicator | Must have |
| FR-19 | A task whose due date is earlier than today and whose status is not Done is marked as overdue in every task list. The marking includes a text label or symbol and does not rely on colour alone. | Project Manager, Team Member, Viewer | F40 Overdue highlighting | Must have |
| FR-20 | Members can send feedback, such as a problem report or a suggestion, from inside Matriz. | All user types | F44 Send feedback | Should have |
| FR-21 | Members can request a copy or the deletion of their personal data. In the MVP, these requests may be handled manually by the support team. | All user types | F45 Personal data requests | Must have |

## Non-functional requirements

All non-functional requirements are based on assumptions and must be confirmed. Numeric targets that haven't been decided are marked as pending decision [PD-15](constraints.md#pending-product-decisions) instead of being invented.

| ID | Requirement | Measure / verification | Basis |
|---|---|---|---|
| NFR-01 | **Workspace data isolation.** Each organization can see and change only its own data. | Pass/fail: a member of workspace A cannot see or change any project, task, comment or member of workspace B. | Assumption AQ24 |
| NFR-02 | **Data protection.** Matriz follows strict general data-protection rules, such as the EU's GDPR. | Pass/fail: personal-data requests (FR-21) can be fulfilled. The response-time target is pending (PD-15). | Assumption AQ24 |
| NFR-03 | **No loss of saved work.** | Pass/fail: no change that a user saved successfully is lost. | Assumption AQ27 |
| NFR-04 | **Availability during working hours.** A brief interruption is tolerable. | The numeric availability target is pending (PD-15). | Assumption AQ27 |
| NFR-05 | **Capacity.** The pilot has tens of teams and up to a few hundred users. Growth to thousands of users is expected within one to three years. | Exact capacity targets are pending (PD-15). | Assumption AQ25 |
| NFR-06 | **Accessibility.** Text is readable, every function can be used without a mouse, and no information is conveyed by colour alone. | Pass/fail per function for mouse-free use and for colour-independent information (for example AC-18.1). The target conformance standard is pending (PD-15). | Assumption AQ28 |
| NFR-07 | **Learnability.** Matriz is usable without training or a manual. | The target share of pilot users who complete core tasks unaided is pending (PD-15). | AQ6 (confirmed), assumption AQ9 |
| NFR-08 | **Primary use on computers at work.** | Pass/fail: every MVP function can be used on a computer. Comfortable phone use is outside the initial scope (F50). Offline use is not planned (F51). | Assumption AQ26 |
| NFR-09 | **One interface language at launch.** | Pass/fail: the whole interface is available in the launch language. The choice of language is pending decision PD-13. | Assumption AQ10 |
| NFR-10 | **Data sensitivity.** Matriz stores moderately confidential business information and no payment data in the MVP. | Pass/fail: the MVP collects no payment details. | Assumption AQ23 |

Security and privacy aspects of these requirements are summarized in [constraints.md](constraints.md#security-and-privacy-requirements).

## MVP scope

**Core value:** a work team can see in one place what needs to be done, who is responsible, by when, and how far each project has progressed.

**Included:** FR-01 to FR-21. That is **18 Must have** requirements plus **3 Should have** requirements (FR-05 Read-only access, FR-15 Filter tasks and FR-20 Send feedback). All non-functional requirements (NFR-01 to NFR-10) apply to the MVP.

**Success criterion:** OBJ-05, meaning most pilot teams keep using Matriz for their real projects after three months.

**Excluded:** everything listed in [Features outside the initial scope](#features-outside-the-initial-scope).

**Before design starts:** the five assumptions that shape the MVP most (AQ3, AQ6, AQ7, AQ11 and AQ17) were confirmed on 2026-10-29, which resolves the former PD-14. See the [decision log](constraints.md#decision-log). The remaining pending decisions still need to be made before the features they affect are designed.

## Features outside the initial scope

These features keep their IDs from the functional proposal. None of them is part of the MVP or the user stories. The order of the planned releases should follow pilot feedback.

| ID | Feature | Priority | Planned for |
|---|---|---|---|
| F06 | Personal profile | Should have | Release 2 |
| F07 | External guest access | Should have | Release 2 |
| F17 | Task priority | Should have | Release 2 |
| F18 | Subtasks or checklists | Should have | Release 2 |
| F27 | Search | Should have | Release 2 |
| F28 | Board view | Should have | Release 2 |
| F32 | File attachments | Should have | Release 2 |
| F33 | Task history | Should have | Release 2 |
| F37 | Email notifications | Should have | Release 2 |
| F38 | Due-date reminders | Should have | Release 2 |
| F41 | Manager overview | Should have | Release 2 |
| F46 | Subscription and billing | Should have | Release 2 |
| F49 | Additional languages | Should have | Release 2 |
| F50 | Comfortable phone use | Should have | Release 2 |
| F08 | Sign in with an existing company account | Could have | Later |
| F12 | Project templates | Could have | Later |
| F19 | Custom statuses | Could have | Later |
| F20 | Task dependencies | Could have | Later |
| F21 | Recurring tasks | Could have | Later |
| F22 | Labels or tags | Could have | Later |
| F29 | Calendar view | Could have | Later |
| F30 | Timeline view | Could have | Later |
| F34 | Mentions | Could have | Later |
| F42 | Workload view | Could have | Later |
| F43 | Reports and export | Could have | Later |
| F47 | Import from other tools | Could have | Later |
| F48 | Connections to other tools | Could have | Later |
| F23 | Time tracking | Won't have (for now) | Not planned |
| F35 | Team chat | Won't have (for now) | Not planned |
| F51 | Offline use | Won't have (for now) | Not planned |

Sending invitations by email (FR-03) is part of the MVP. It is not the same as F37 Email notifications, which is outside the initial scope.

Some product categories are excluded entirely. See [Scope limitations](constraints.md#scope-limitations).
