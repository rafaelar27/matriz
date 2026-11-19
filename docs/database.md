# Matriz — Data Model

> **Status:** draft, written on 2026-11-05. This is a conceptual and logical model only. The data store is PostgreSQL, a relational database (see the [decision log](constraints.md#decision-log), 2026-11-12). Hosting is pending (TD-02). Types are generic. Business rules marked **BR-xx** are confirmed by the documentation. Items marked **DA-xx** are assumptions made for this model and must be confirmed before they are treated as rules. Requirement IDs are defined in [requirements.md](requirements.md), user stories in [user-stories.md](user-stories.md), constraints and pending decisions in [constraints.md](constraints.md), and components (CMP-xx) in [architecture.md](architecture.md).

## Purpose and scope of the model

The model describes the data Matriz must keep to support the MVP: FR-01 to FR-22 and US-01 to US-29. It follows the data-ownership rule of the [architecture](architecture.md#decision): each entity is owned by one component, and other components reach it only through that component.

**In scope:** accounts, workspaces and their members, invitations, projects and project members, tasks, task assignees, comments, in-app notifications, feedback and personal-data requests.

**Not modelled:**

- **Derived values.** A project's progress (FR-18) and whether a task is overdue (FR-19) are calculated from task data and not stored (BR-15, BR-16).
- **Undecided behaviour.** There are no attributes for project statuses (PD-05), invitation expiry or resending (PD-09), concurrent-edit handling (PD-11, TD-07) or a notification's read state, which FR-17 does not mention (PD-22). See [Open questions and pending decisions](#open-questions-and-pending-decisions).
- **Sign-in sessions.** Sessions are kept in the application's memory, not in the database (TD-03, decided 2026-11-12).
- **Features outside the initial scope.** For example task priority (F17), subtasks (F18), attachments (F32), task history (F33), email notifications (F37), labels (F22), guest access (F07) and billing (F46).

Attribute types are generic: `id` (a unique identifier), `string` (short text), `text` (long text), `boolean`, `date`, `datetime` and `enum` (a fixed list of values).

**Physical representation.** In PostgreSQL: ids are UUIDs (`gen_random_uuid()`); string and text are `TEXT` without maximum lengths (the limits are checked by the modules); enums are stored as upper-case codes with CHECK constraints (for example `TO_DO`, `IN_PROGRESS`, `DONE`; `ACTIVE`, `DEACTIVATED`); datetimes are `TIMESTAMPTZ`. Approved with INC-01 on 2026-11-12.

## Entities summary

| Entity | Description | Owning component | Related FR / US |
|---|---|---|---|
| **Account** | A person's login identity. One account belongs to one person. | CMP-01 Accounts and Sign-in | FR-01; US-01 |
| **Workspace** | An organization's space, holding its own projects and members. | CMP-02 Workspaces and Members | FR-02; US-02 |
| **Member** | An account's membership of one workspace, with one role and a status. Resolves the many-to-many relationship between Account and Workspace. | CMP-02 Workspaces and Members | FR-03, FR-04, FR-05; US-03, US-04, US-05, US-19 |
| **Invitation** | An invitation, sent by email address, to join a workspace with a given role. | CMP-02 Workspaces and Members | FR-03, FR-04; US-03 |
| **Project** | A body of work in a workspace, with a name, owner and dates. | CMP-04 Projects | FR-06, FR-08; US-06, US-07, US-09 |
| **ProjectMember** | A member's participation in a project. Resolves the many-to-many relationship between Project and Member. | CMP-04 Projects | FR-07; US-08 |
| **Task** | One action in a project, with exactly one owner, an optional due date and a status. | CMP-05 Tasks | FR-09, FR-10, FR-11, FR-12; US-10, US-11 |
| **TaskAssignee** | A member assigned to a task in addition to its owner. Resolves the many-to-many between Task and Member. | CMP-05 Tasks | FR-22; US-29 |
| **Comment** | A message added by a member to a task. | CMP-07 Comments | FR-16; US-15 |
| **Notification** | An in-app notice to a member about a task assignment or a comment on a task they own. | CMP-08 Notifications | FR-17; US-16 |
| **Feedback** | A problem report or suggestion sent by a member. | CMP-10 Feedback and Personal Data Requests | FR-20; US-20 |
| **PersonalDataRequest** | A person's request for a copy or the deletion of their personal data, handled manually by the support team. | CMP-10 Feedback and Personal Data Requests | FR-21; US-21 |

**Implementation status (2026-11-19).** V1 creates every entity and V3 adds TaskAssignee. No code reads or writes `invitation`, `notification`, `feedback` or `personal_data_request` in this delivery, because FR-03, FR-17, FR-20 and FR-21 are not built. `project.is_archived` is always false (no archive operation, FR-06, US-07). `workspace` and `member` rows are also written when a workspace is created (INC-14, FR-02): the creator's first membership has role Workspace Administrator and status ACTIVE. Otherwise `member.role` and `member.status` are only read (no role change or deactivation, FR-03, FR-04). Deleting a task or project still cascades to its notifications (DA-05, BR-21).

### Requirement coverage

| FR | Supported by |
|---|---|
| FR-01 Sign up and sign in | Account (password_hash, TD-03) |
| FR-02 Workspace creation | Workspace, Member |
| FR-03 Invite and remove members | Invitation, Member.status |
| FR-04 Roles and permissions | Member.role, Invitation.role |
| FR-05 Read-only access | Member.role = Viewer (enforcement in CMP-03) |
| FR-06 Create, edit and archive projects | Project |
| FR-07 Project members | ProjectMember |
| FR-08 Project list | Project (owner, due date), Workspace |
| FR-09 Create, edit and delete tasks | Task (title, description, created_by_member_id) |
| FR-10 Assign a responsible person | Task.owner_member_id, ProjectMember |
| FR-11 Due dates | Task.due_date |
| FR-12 Task status | Task.status |
| FR-13 Project task list | Task, Member, Account (owner's name) |
| FR-14 "My tasks" | Task.owner_member_id, Task.due_date |
| FR-15 Filter tasks | Task.owner_member_id, Task.status, Task.due_date |
| FR-16 Task comments | Comment |
| FR-17 In-app notifications | Notification |
| FR-18 Project progress indicator | Task.status, derived (BR-15) |
| FR-19 Overdue highlighting | Task.due_date, Task.status, derived (BR-16) |
| FR-20 Send feedback | Feedback |
| FR-21 Personal data requests | PersonalDataRequest |
| FR-22 Task assignees | TaskAssignee, Task, Member |

### User story coverage

| Story | Supported by |
|---|---|
| US-01 Sign in | Account |
| US-02 Create a workspace | Workspace, Member |
| US-03 Invite with a role | Invitation, Member |
| US-04 Change a member's role | Member.role |
| US-05 Deactivate a member | Member.status |
| US-06 Create a project | Project |
| US-07 Archive a project | Project.is_archived |
| US-08 Choose project members | ProjectMember |
| US-09 Project list | Project |
| US-10 Create a task with owner and due date | Task |
| US-11 Change a task's status | Task.status |
| US-12 Project task list | Task |
| US-13 "My tasks" | Task |
| US-14 Filter tasks | Task |
| US-15 Comment on a task | Comment |
| US-16 Notifications | Notification |
| US-17 Progress indicator | Task.status (derived) |
| US-18 Overdue marking | Task.due_date, Task.status (derived) |
| US-19 Read-only access for Viewers | Member.role, Project, Task |
| US-20 Send feedback | Feedback |
| US-21 Personal data requests | PersonalDataRequest |
| US-22 Create a personal account | Account |
| US-23 Project Manager invites a member | Invitation, Member |
| US-24 Edit a project | Project |
| US-25 Edit a task | Task |
| US-26 Delete a task I created | Task (created_by_member_id) |
| US-27 Delete a project | Project, ProjectMember, Task, Comment, Notification |
| US-28 Search projects by name | Project.name |
| US-29 Assign members to a task | TaskAssignee, ProjectMember |

## Attributes per entity

"Personal data" marks attributes with privacy implications; see [Security and privacy notes](#security-and-privacy-notes).

### Account

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| account_id | id | PK | Yes | Identifier of the account. |
| name | string | | Yes | The person's name, shown as task owner and comment author (FR-13, FR-16). **Personal data.** Required: DA-02. |
| email | string | UK | Yes | The person's email address. Used to match invitations to people (FR-03, EC-01). **Personal data.** Uniqueness: DA-01. |
| password_hash | string | | Yes | BCrypt hash of the account's password (TD-03, decided 2026-11-12). Never the password itself; never returned in responses or written to logs (SP-01, AGENTS.md C2). |

### Workspace

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| workspace_id | id | PK | Yes | Identifier of the workspace. |
| name | string | | Yes | The organization's workspace name. Required: DA-02. |

### Member

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| member_id | id | PK | Yes | Identifier of the membership. |
| workspace_id | id | FK → Workspace, UK (with account_id) | Yes | The workspace. |
| account_id | id | FK → Account, UK (with workspace_id) | Yes | The person. |
| role | enum (Workspace Administrator, Project Manager, Team Member, Viewer) | | Yes | The member's single role (FR-04, BR-03). |
| status | enum (active, deactivated) | | Yes | Deactivated members can no longer access the workspace (FR-03, SP-04). |

### Invitation

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| invitation_id | id | PK | Yes | Identifier of the invitation. |
| workspace_id | id | FK → Workspace | Yes | The workspace the person is invited to. |
| email | string | | Yes | The invitee's email address (FR-03, AC-03.2). **Personal data**, possibly of someone who is not yet a member. |
| role | enum (Workspace Administrator, Project Manager, Team Member, Viewer) | | Yes | The role given on acceptance (AC-03.3). Which roles a Project Manager may give: PD-02. |
| invited_by_member_id | id | FK → Member | Yes | The Workspace Administrator or Project Manager who sent it (FR-03). |
| status | enum (pending, accepted) | | Yes | Whether the invitation has been accepted (AC-03.1, EC-02). Other states: PD-09. |

### Project

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| project_id | id | PK | Yes | Identifier of the project. |
| workspace_id | id | FK → Workspace | Yes | The workspace the project belongs to (FR-02, NFR-01). |
| name | string | | Yes | The project's name, 3 to 100 characters (FR-06, BR-21). |
| description | text | | No (DA-03) | The project's description, at most 1000 characters (FR-06, BR-21). |
| owner_member_id | id | FK → Member | Yes (decided 2026-11-12) | The project's owner: the creator unless another active member of the workspace is chosen (FR-06, FR-08, BR-23). |
| start_date | date | | No (DA-03) | The project's start date, not after the due date when both are given (FR-06, BR-21). |
| due_date | date | | No (DA-03) | The project's due date, not before the start date when both are given (FR-06, FR-08, BR-21). |
| is_archived | boolean | | Yes | Whether the project has been archived (FR-06, US-07). What archiving changes: PD-05 (DA-11). |

Migration V4 makes `owner_member_id` required (NOT NULL) and adds the check constraint `project_date_order_ck` (`start_date IS NULL OR due_date IS NULL OR start_date <= due_date`). That the owner is an active member of the project's workspace spans rows, so CMP-04 Projects checks it when the owner is chosen (BR-23, BR-18).

### ProjectMember

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| project_id | id | PK, FK → Project | Yes | The project. |
| member_id | id | PK, FK → Member | Yes | The member taking part (FR-07). |

The primary key is the pair (project_id, member_id), so a member takes part in a project at most once.

### Task

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| task_id | id | PK | Yes | Identifier of the task. |
| project_id | id | FK → Project | Yes | The project the task belongs to (FR-09). |
| title | string | | Yes | The task's title, 3 to 150 characters after trimming (FR-09, AC-10.2, BR-10; decided 2026-11-12). |
| description | text | | No (DA-04) | The task's description, at most 2000 characters (FR-09; decided 2026-11-12). Whether required: PD-04. |
| owner_member_id | id | FK → Member | Yes | The task's single owner (FR-10, BR-09). |
| due_date | date | | No (DA-04) | The task's due date (FR-11). Whether required: PD-04. |
| status | enum (To do, In progress, Done) | | Yes | The task's status; a new task starts as To do (FR-12, BR-11). |
| created_by_member_id | id | FK → Member | Yes | Who created the task, which decides who may delete it (FR-09, BR-12). |

### TaskAssignee

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| task_id | id | PK, FK → Task | Yes | The task (FR-22). Deleting the task deletes its assignees. |
| member_id | id | PK, FK → Member | Yes | The assigned member, an active member of the task's project when assigned (BR-22). |
| assigned_at | datetime | | Yes | When the member was assigned, set by the server. |

The primary key (task_id, member_id) makes a second assignment of the same member to the same task impossible (BR-22).

### Comment

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| comment_id | id | PK | Yes | Identifier of the comment. |
| task_id | id | FK → Task | Yes | The task commented on (FR-16). |
| author_member_id | id | FK → Member | Yes | The member who wrote it (FR-16). |
| body | text | | Yes | The comment text. Never empty or spaces only (BR-13). May contain personal data entered by users. |
| created_at | datetime | | Yes | When it was posted, used to order comments (DA-07). |

### Notification

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| notification_id | id | PK | Yes | Identifier of the notification. |
| recipient_member_id | id | FK → Member | Yes | The member notified (FR-17). |
| type | enum (task assigned, comment added) | | Yes | The event that caused it (FR-17). |
| task_id | id | FK → Task | Yes | The task concerned. |
| comment_id | id | FK → Comment | Only when type is "comment added" | The comment that caused it. |
| created_at | datetime | | Yes | When it was created, used to order notifications (DA-07). |

### Feedback

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| feedback_id | id | PK | Yes | Identifier of the feedback. |
| member_id | id | FK → Member | Yes | The member who sent it (FR-20). |
| message | text | | Yes | The problem report or suggestion. May contain personal data entered by users. |
| created_at | datetime | | Yes | When it was sent, used by the support team to order feedback (DA-07). |

### PersonalDataRequest

| Attribute | Type | Key | Required | Description |
|---|---|---|---|---|
| request_id | id | PK | Yes | Identifier of the request. |
| account_id | id | FK → Account | Yes | The person making the request (FR-21, DA-10). |
| type | enum (copy, deletion) | | Yes | What is requested (FR-21). |
| status | enum (received, completed) | | Yes | Progress of manual handling by the support team (DA-06). |
| requested_at | datetime | | Yes | When the request was made. Needed to measure the response time once set (NFR-02, PD-15). |
| completed_at | datetime | | No | When the support team completed it (NFR-02, PD-15). |

## Relationships and cardinalities

"1" means exactly one, "0..1" at most one, "0..*" zero or more and "1..*" one or more. Each side reads "a [first entity] has … of the second".

| ID | Relationship | Entities | Cardinality | Description |
|---|---|---|---|---|
| R-01 | holds | Account → Member | 1 to 0..* | An account holds zero or more memberships; a member belongs to exactly one account. Together with R-02, this resolves the many-to-many between Account and Workspace (PD-16 may restrict it to one workspace per account). |
| R-02 | has | Workspace → Member | 1 to 1..* | A workspace has at least one member, because it always keeps a Workspace Administrator (SP-07); a member belongs to exactly one workspace. |
| R-03 | receives | Workspace → Invitation | 1 to 0..* | A workspace has zero or more invitations; each invitation is for exactly one workspace (FR-03). |
| R-04 | sends | Member → Invitation | 1 to 0..* | A member sends zero or more invitations; each invitation has exactly one sender (FR-03). |
| R-05 | contains | Workspace → Project | 1 to 0..* | A workspace contains zero or more projects; each project belongs to exactly one workspace (FR-02, FR-08). |
| R-06 | owns | Member → Project | 1 to 0..* | A member owns zero or more projects; each project has exactly one owner (FR-06, decided 2026-11-12). |
| R-07 | includes | Project → ProjectMember | 1 to 0..* | A project has zero or more project members; each project member is for exactly one project (FR-07). Deleting the project deletes its project members (BR-21). |
| R-08 | takes part as | Member → ProjectMember | 1 to 0..* | A member takes part in zero or more projects; each project member is exactly one member (FR-07). Together with R-07, this resolves the many-to-many between Project and Member. |
| R-09 | contains | Project → Task | 1 to 0..* | A project contains zero or more tasks; each task belongs to exactly one project (FR-09). Deleting the project deletes its tasks, and through DA-05 their comments and notifications (BR-21). |
| R-10 | owns | Member → Task | 1 to 0..* | A member owns zero or more tasks; each task has exactly one owner (FR-10). |
| R-11 | creates | Member → Task | 1 to 0..* | A member creates zero or more tasks; each task has exactly one creator (FR-09). |
| R-12 | has | Task → Comment | 1 to 0..* | A task has zero or more comments; each comment is on exactly one task (FR-16). |
| R-13 | writes | Member → Comment | 1 to 0..* | A member writes zero or more comments; each comment has exactly one author (FR-16). |
| R-14 | receives | Member → Notification | 1 to 0..* | A member receives zero or more notifications; each has exactly one recipient (FR-17). |
| R-15 | concerns | Task → Notification | 1 to 0..* | A task is the subject of zero or more notifications; each notification concerns exactly one task (FR-17). |
| R-16 | triggers | Comment → Notification | 0..1 to 0..1 | A comment triggers at most one notification, to the task's owner (none if PD-12 excludes self-notification); a notification refers to at most one comment (FR-17). |
| R-17 | sends | Member → Feedback | 1 to 0..* | A member sends zero or more feedback items; each has exactly one sender (FR-20). |
| R-18 | makes | Account → PersonalDataRequest | 1 to 0..* | A person makes zero or more personal-data requests; each request is from exactly one account (FR-21, DA-10). |
| R-19 | is worked on by | Task → TaskAssignee | 1 to 0..* | A task has zero or more assignees; each assignment is for exactly one task (FR-22). |
| R-20 | is assigned as | Member → TaskAssignee | 1 to 0..* | A member is assigned to zero or more tasks; each assignment is exactly one member, at most once per task (FR-22, BR-22). |

**Many-to-many relationships:**

- **Account and Workspace** are resolved by **Member**, which is needed because it stores the role (FR-04) and status (FR-03) of each membership.
- **Project and Member** are resolved by **ProjectMember**. It has no attributes of its own in the MVP. It is kept as an entity because a logical model cannot express a many-to-many link directly, and it makes "at most once per project" a key rule.

**Why Task owner points to Member and not ProjectMember:** a task must stay valid if its owner leaves the project or is deactivated (EC-04, EC-09), until PD-10 is decided. The rule that the owner is a project member is therefore BR-09, checked when the owner is assigned, not a structural constraint.

## Business rules

These are confirmed by the documentation. Not enforced in this delivery, because their features are not built: BR-03 (changing roles), BR-04, BR-06, BR-08 (choosing project members), BR-14, BR-16, BR-17. BR-05 is enforced only by `member_workspace_account_uk`. BR-01 is enforced by `createWorkspace` since INC-14; the name rule is checked in the module, not by the database.

| ID | Rule | Source |
|---|---|---|
| BR-01 | Each account belongs to one person only. Any account holder can create a workspace, also one who already belongs to other workspaces. A workspace's name is required, trimmed, 3 to 100 characters long, and need not be unique; a name containing a NUL character (U+0000) is refused, because the database cannot store it, while numeric and boolean JSON names are accepted and stored as text (decided 2026-11-19). An account can create any number of workspaces. The creator becomes its first member, as a Workspace Administrator, in the same transaction. | FR-01, FR-02, SP-07, decision log 2026-10-29 and 2026-11-19 |
| BR-02 | Workspace content is only available to signed-in, active members of that workspace. | FR-01, SP-01, SP-04 |
| BR-03 | Each member holds exactly one of the four roles: Workspace Administrator, Project Manager, Team Member, Viewer. Workspace Administrators assign and change roles. | FR-04, AQ11 (confirmed) |
| BR-04 | A workspace always keeps at least one active Workspace Administrator. Changes that would remove the last one are refused. | SP-07, EC-03 |
| BR-05 | An account is a member of a given workspace at most once. Inviting an existing member is refused, and accepting a duplicate invitation creates no second member. | EC-01, EC-02 |
| BR-06 | An invitation needs a valid email address (containing "@") and a role. | AC-03.2, AC-03.3 |
| BR-07 | Deactivated members cannot access the workspace. Their tasks are not deleted and keep their comments; they stay in task lists and the progress count. | FR-03, SP-04, EC-04 |
| BR-08 | Any workspace member, including a Viewer, can be a project member. Project members belong to the project's workspace. | FR-07, NFR-01, decision log 2026-10-29 |
| BR-09 | Each task has exactly one owner, who must be an active member of the task's project when assigned. A Viewer can be a task owner. | FR-10, AC-10.3, AC-10.4 |
| BR-10 | A task's title is required. It is trimmed and must be 3 to 150 characters long, so a title of only spaces is refused. | FR-09, AC-10.2, AC-10.5, decision log 2026-11-12 |
| BR-11 | A task has exactly one of three statuses (To do, In progress, Done). A new task starts as To do. | FR-12, AC-10.1, AC-11.2 |
| BR-12 | A member can delete the tasks they created, but not tasks created by others. Project Managers and Team Members can create tasks, and so can Workspace Administrators in projects they are members of. | FR-09, decision log 2026-10-29 and 2026-11-19 |
| BR-13 | Empty comments, or comments containing only spaces, are not saved. A comment is stored trimmed and is 1 to 2000 characters long. Its author and created_at are set by the server. | FR-16, EC-08, decision log 2026-11-12 |
| BR-14 | A notification is created when a member is assigned a task, and when someone comments on a task the member owns. | FR-17, US-16 |
| BR-15 | A project's progress is the number of its tasks with status Done out of all its tasks, for example "2 of 8 tasks done", and "0 of 0 tasks done" for a project with no tasks. Deleted tasks are not counted. It is derived, not stored. | FR-18, AC-17.1 to AC-17.3, EC-05 |
| BR-16 | A task is overdue when its due date is earlier than today and its status is not Done. A task without a due date is never overdue. It is derived, not stored. | FR-19, AC-18.2, AC-18.3, EC-06 |
| BR-17 | "My tasks" contains the tasks whose owner is the signed-in member, ordered by due date with the earliest first. | FR-14, AC-13.1, AC-13.2 |
| BR-18 | Every record belongs to exactly one workspace, directly or through its parent, and never references records in another workspace. | NFR-01, SP-02 |
| BR-19 | No payment data is stored. | NFR-10, SP-06 |
| BR-20 | The customer organization owns the data stored in its workspace. | SP-05, decision log 2026-10-29 |
| BR-21 | A Project Manager or a Workspace Administrator of the workspace (Workspace Administrators since 2026-11-19, PD-03 in part) can create, edit and delete a project. Deleting it also deletes its project members and its tasks with their comments and notifications, and cannot be undone. A project's name is 3 to 100 characters long and its description at most 1000 characters. When both are given, a project's start date is not after its due date. The creator of a project becomes one of its project members. | FR-06, US-27, decision log 2026-11-12 and 2026-11-19 |
| BR-22 | A task can have any number of assignees besides its owner. Only active members of the task's project can be assigned, each at most once per task. Assignments do not change the owner. | FR-22, US-29, decision log 2026-11-12 |
| BR-23 | Each project has exactly one owner: its creator unless another active member of the project's workspace is chosen. The owner must be an active member of that workspace when chosen; an empty owner is refused. | FR-06, decision log 2026-11-12 |

## Assumptions to confirm

These are not confirmed. Each one must be confirmed or replaced by the project decision-maker (KC-01).

| ID | Assumption | Rationale | Impact if wrong |
|---|---|---|---|
| DA-01 | An account's email address is unique, and invitations are matched to people by email address. | EC-01 needs to recognise that an address "already belongs to a member"; FR-03 invites by email. | Matching invitations to accounts would need another identifier; Account.email would lose its UK. |
| DA-02 | Account name and Workspace name are required. *Confirmed for the Workspace name on 2026-11-19:* required, 3 to 100 characters after trimming (BR-01). Still assumed for the Account name. | Owners and authors must be shown by name (FR-13, FR-16); AQ23 (**assumption**) lists names among stored data. | The attributes become optional, and lists need a fallback display. |
| DA-03 | *Rejected in part 2026-11-12:* a project always has an owner (see the [decision log](constraints.md#decision-log); enforced by migration V4 and CMP-04 Projects since INC-07, BR-23). Still assumed: only a project's name is required; description, start date and due date are optional. | FR-06 lists the fields but not which are required (comparable to PD-04 for tasks). | Making a field required changes it from optional to required, as happened for the owner (R-06 became 1 to 0..*, INC-07). |
| DA-04 | A task's description and due date are optional until PD-04 is decided. | Optional fits both outcomes of PD-04; EC-06 already covers tasks without a due date. | If PD-04 makes them required, the attributes become required; no structural change. |
| DA-05 | Deleting a task also deletes its comments and notifications. | FR-09 allows deletion and AC-17.3 removes the task from the count; nothing suggests keeping orphan comments. | If they must be kept, Comment and Notification need to survive without their task, and R-12 and R-15 become optional on the task side. |
| DA-06 | A personal-data request is either received or completed. | FR-21 allows manual handling, which needs a minimal status to track. | Status values change (for example adding "rejected"); no structural change. |
| DA-07 | Comments, notifications and feedback record when they were created, to order them. | Lists of these need a sensible order; no requirement states one. | The ordering attribute or rule changes. |
| DA-08 | *Superseded 2026-11-12:* TD-03 was decided, and Account.password_hash was added. Previously: no credential data is modelled until TD-03 decides how sign-in works. | Modelling credentials would pre-empt TD-03. | Once TD-03 is decided, credential attributes or a separate entity are added to Account, under SP-01. |
| DA-09 | Workspace membership of tasks, comments, notifications and project members is found through their parent records, not stored on every record. | Avoids redundant attributes. This is how this model reads architecture rule 4 ("every record that belongs to a workspace is marked with its workspace"). | If every record must carry its own workspace identifier, a workspace_id attribute is added to those entities, with a rule that it matches the parent's. |
| DA-10 | Personal-data requests are linked to the account, not to a membership. | Personal data such as name and email belongs to the person, not to one workspace. | If requests are per workspace, PersonalDataRequest links to Member instead. |
| DA-11 | Archiving is recorded as a yes/no flag on the project. | FR-06 says projects can be archived; PD-05 decides what archiving changes. | If PD-05 introduces project statuses, the flag is replaced by a status attribute. |

The model also relies on AQ23 (stored data is moderately confidential, with no payment data) and AQ24 (strict data-protection rules). Both are **assumptions** in [constraints.md](constraints.md#assumptions-made).

## Security and privacy notes

| Item | Attributes or entities | Implication | Reference |
|---|---|---|---|
| Personal data of members | Account.name, Account.email | Personal data under strict data-protection rules. Must be included in a copy request and handled in a deletion request. | SP-05, NFR-02, FR-21, AQ23, AQ24 |
| Personal data of non-members | Invitation.email | Stored for people who may never join. How long pending invitations are kept is open (PD-09). | NFR-02, SP-05 |
| Free text written by users | Project.description, Task.title, Task.description, Comment.body, Feedback.message | May contain personal data typed by users. Relevant to copy and deletion requests (PD-18). | FR-21, PD-18 |
| Personal-data requests | PersonalDataRequest | Sensitive in itself. Which support staff can access it, and how the requester is verified, is pending (PD-19). | FR-21, SP-05, PD-19, TD-09 |
| Credentials | Account.password_hash | Only a BCrypt hash is stored; passwords and hashes are never returned in responses or written to logs. | SP-01, TD-03, AGENTS.md C2 |
| Workspace isolation | All entities except Account and PersonalDataRequest | Every read and change is limited to the signed-in member's workspace. | NFR-01, SP-02, BR-18 |
| Access by role | Member.role, Member.status | Permissions are set by role; deactivated members lose access. | SP-03, SP-04 |
| Payment data | None | No entity or attribute holds payment data. | SP-06, NFR-10 |

## ER diagram

The ER diagram is kept in a separate file: [database-diagram.md](database-diagram.md). It shows every entity, its keys and the attributes in the tables above, with relationship labels matching the R-xx IDs.

## Open questions and pending decisions

### Assumptions made in this model

DA-01 to DA-11 above need confirmation, except DA-08 (superseded on 2026-11-12) and the owner part of DA-03 (rejected on 2026-11-12). DA-03 (description and dates), DA-05 and DA-06 were proposed during planning and not yet answered.

### Pending product decisions that affect the model

| ID | Effect on the model |
|---|---|
| PD-01 | Who may edit tasks or assign them to others. Permission rule only; no structural change expected. |
| PD-02 | Which roles a Project Manager may give in an Invitation. Rule only. |
| PD-03 | If a member may hold more than one role, Member.role becomes a separate entity or a list. |
| PD-04 | Whether Task.description and Task.due_date are required (DA-04). The lengths were decided on 2026-11-12. |
| PD-05 | Project statuses and the effect of archiving may replace Project.is_archived (DA-11). |
| PD-06 | Whether Done tasks can go back to an earlier status and appear in "My tasks". Rule only. |
| PD-07 | Progress percentage and rounding. Derived; no stored data. |
| PD-08 | The time zone for "today" may require a time-zone attribute on Workspace or Account. Placement of tasks without a due date in "My tasks" is a rule only. |
| PD-09 | Resending, expiry and display of pending invitations may add attributes to Invitation. |
| PD-10 | What happens to tasks of deactivated or removed members may add a "needs reassignment" marker to Task. |
| PD-11 | Which change wins in simultaneous edits may require a version attribute on Task (TD-07). |
| PD-12 | Whether self-notifications exist. Rule only (R-16 already allows zero). |
| PD-16 | If one account may join only one workspace, R-01 becomes 1 to 0..1. That the workspace creator becomes its Workspace Administrator was decided on 2026-11-19. |
| PD-18 | What a deletion request removes, given that tasks and comments reference the member who created or wrote them, and what happens when the only Workspace Administrator asks for deletion (BR-04). |
| PD-19 | Identity verification and support-staff access for PersonalDataRequest. |

### Pending technical decisions that affect the model

| ID | Effect on the model |
|---|---|
| TD-02 | Where data is stored and hosted. This model is independent of it. |
| TD-07 | Version or timestamp attributes for concurrent edits on Task. |
| TD-08 | How notifications reach the member; may add delivery attributes to Notification. |

### Other open questions

- **Notification read state (PD-22).** FR-17 does not say whether notifications can be marked as read. Nothing is modelled for this.
- **Feedback category (PD-23).** FR-20 gives "a problem report or a suggestion" as examples, not as a fixed list, so no category is modelled.
- **Architecture rule 4.** DA-09 reads "marked with its workspace" as "directly or through its parent". This should be confirmed and, if agreed, clarified in [architecture.md](architecture.md#decision).
