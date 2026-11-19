# Matriz — User Stories

> **Status:** draft, pending client validation. Requirement IDs (FR-xx, NFR-xx) are defined in [requirements.md](requirements.md). Pending decisions (PD-xx) are listed in [constraints.md](constraints.md#pending-product-decisions). Where a story depends on an unconfirmed point, it refers to the pending decision instead of inventing a rule.

## MVP user stories

| ID | User story |
|---|---|
| US-01 | **As a** Team Member, **I want** to sign in to my account, **so that** I can reach my workspace's projects and tasks. |
| US-02 | **As a** Workspace Administrator, **I want** to create a workspace for my organization, **so that** our projects and members are kept in one space, separate from other organizations. |
| US-03 | **As a** Workspace Administrator, **I want** to invite a colleague by email address and give them a role, **so that** they can join the workspace with the right permissions. |
| US-04 | **As a** Workspace Administrator, **I want** to change a member's role, **so that** their permissions match their current responsibilities. |
| US-05 | **As a** Workspace Administrator, **I want** to deactivate a member who has left the organization, **so that** they can no longer access the workspace. |
| US-06 | **As a** Project Manager, **I want** to create a project with a name, description, owner, start date and due date, **so that** the team's work is grouped under a defined goal and timeframe. *(Workspace Administrators too, decided 2026-11-19.)* |
| US-07 | **As a** Project Manager, **I want** to archive a finished project, **so that** finished work is separated from ongoing work. *(What archiving changes: PD-05.)* |
| US-08 | **As a** Project Manager, **I want** to choose which workspace members take part in a project, **so that** only those people can be assigned its tasks. |
| US-09 | **As a** Project Manager, **I want** to see a list of all projects in the workspace with their owner and due date, **so that** I have one overview of all ongoing work. |
| US-10 | **As a** Project Manager, **I want** to create a task in a project and give it one owner and a due date, **so that** everyone knows who is responsible and by when. *(Workspace Administrators too, in projects they are members of, decided 2026-11-19.)* |
| US-11 | **As a** Team Member, **I want** to change the status of my task to To do, In progress or Done, **so that** others can see how my work is progressing. |
| US-12 | **As a** Team Member, **I want** to see all tasks in a project with their owner, status and due date, **so that** I understand what the whole team is working on. |
| US-13 | **As a** Team Member, **I want** to see a list of all tasks assigned to me across projects, ordered by due date, **so that** I know what to work on next. |
| US-14 | **As a** Project Manager, **I want** to filter a project's task list by owner, status or due date, **so that** I see only the tasks I need to follow up on. |
| US-15 | **As a** Team Member, **I want** to comment on a task, **so that** discussion about that task stays with the task instead of in separate emails. *(Workspace Administrators too, in projects they are members of, decided 2026-11-19.)* |
| US-16 | **As a** Team Member, **I want** to be notified inside Matriz when I am assigned a task or when someone comments on a task I own, **so that** I don't miss work that concerns me. |
| US-17 | **As a** Project Manager, **I want** each project to show how many of its tasks are done, **so that** I can see its progress without opening every task. |
| US-18 | **As a** Project Manager, **I want** tasks that are past their due date and not done to be marked as overdue, **so that** I can follow up on delays. |
| US-19 | **As a** Viewer, **I want** to see all of the workspace's projects and tasks without being able to change them, **so that** I can follow progress without risk of accidental edits. |
| US-20 | **As a** Team Member, **I want** to send feedback from inside Matriz, **so that** I can report a problem or suggest an improvement. |
| US-21 | **As a** Team Member, **I want** to request a copy or the deletion of my personal data, **so that** my data-protection rights are respected. |
| US-22 | **As a** Team Member, **I want** to create a personal account, **so that** I can sign in and take part in a workspace. *(Sign-up method: TD-03.)* |
| US-23 | **As a** Project Manager, **I want** to invite a colleague to the workspace by email address, **so that** they can join the work I am organizing. *(Roles a Project Manager may give: PD-02.)* |
| US-24 | **As a** Project Manager, **I want** to edit a project's name, description, owner, start date and due date, **so that** the project reflects current plans. *(Workspace Administrators too, decided 2026-11-19.)* |
| US-25 | **As a** Team Member, **I want** to edit a task, **so that** its details stay correct. *(Who may edit which tasks: PD-01.)* |
| US-26 | **As a** Team Member, **I want** to delete a task I created, **so that** tasks that are no longer needed don't clutter the project. |
| US-27 | **As a** Project Manager, **I want** to delete a project, **so that** projects created by mistake or no longer needed don't clutter the project list. *(Added 2026-11-12. Workspace Administrators too, decided 2026-11-19.)* |
| US-28 | **As a** Team Member, **I want** to search the project list by project name, **so that** I find a project quickly when the workspace has many. *(Added 2026-11-12.)* |
| US-29 | **As a** Project Manager, **I want** to assign several project members to a task besides its owner, and remove them again, **so that** everyone working on the task is visible. *(Added 2026-11-12. Workspace Administrators too, decided 2026-11-19.)* |

## Priorities (MoSCoW)

Each story takes the priority of its primary functional requirement. US-19 traces to FR-05 (Should have) and FR-08 (Must have); its purpose is read-only access, so it takes FR-05's priority.

| Priority | Stories |
|---|---|
| Must have | US-01, US-02, US-03, US-04, US-05, US-06, US-07, US-08, US-09, US-10, US-11, US-12, US-13, US-15, US-16, US-17, US-18, US-21, US-22, US-23, US-24, US-25, US-26, US-27, US-28, US-29 |
| Should have | US-14, US-19, US-20 |
| Could have | None. Could-have features are outside the initial scope. |
| Won't have (for now) | None |

## Acceptance criteria

Every story is in the MVP. The seven stories US-03, US-10, US-11, US-13, US-17, US-18 and US-19 carry the core value and have Given / When / Then criteria. US-15 has criteria since 2026-11-12. US-02 has criteria since 2026-11-19. US-23 to US-28 have criteria for the parts of FR-03, FR-06, FR-08 and FR-09 that are already decided. US-22 has none yet, because the sign-up method is pending (TD-03). Names (Ana, Ben, Carla) and dates are test data. "Today" means the date the test is run.

### US-02: Create a workspace

- **AC-02.1 (success):** **Given** I am signed in, with any role in other workspaces, or with all my memberships deactivated, **when** I create a workspace named "Harbor Works", **then** it exists, and I am its first member with the role "Workspace Administrator", saved together.
- **AC-02.2 (name rule):** **Given** I am creating a workspace, **when** I enter a name of only spaces, or shorter than 3 or longer than 100 characters after removing leading and trailing spaces, or a name that contains a NUL character, **then** no workspace is created and a message states the rule. A name of exactly 3 or 100 characters is accepted, and a stored name has no leading or trailing spaces.
- **AC-02.3 (duplicates):** **Given** a workspace named "Harbor Works" exists, **when** I create another with the same name, **then** it is created.
- **AC-02.4 (separation):** **Given** I created workspace W, **when** a member of another workspace asks for W's members or projects, **then** W is not found and not listed for them (NFR-01).
- **AC-02.5 (fields):** **Given** a request to create a workspace that also names a workspace, role, account or member, **then** it is refused and nothing is created.

### US-03: Invite a member with a role

- **AC-03.1 (success):** **Given** I am signed in as a Workspace Administrator of workspace W, **when** I enter the email address `ana@example.com`, which doesn't belong to any member of W, select the role "Team Member" and send the invitation, **then** an invitation is delivered to `ana@example.com`, and after Ana accepts it she appears in W's member list with the role "Team Member".
- **AC-03.2 (validation):** **Given** I am inviting a member, **when** I enter an address that contains no "@" character (for example `ana.example.com`) and try to send, **then** no invitation is sent and a message states that the email address is invalid.
- **AC-03.3 (validation):** **Given** I am inviting a member, **when** I try to send the invitation without selecting a role, **then** no invitation is sent and a message states that a role is required. *(Basis: FR-04.)*
- **AC-03.4 (permission):** **Given** I am signed in as a Team Member or a Viewer, **when** I open W's member list, **then** no option to invite members is available to me.
- **Pending:** which roles a Project Manager may give an invitee (PD-02).

### US-10: Create a task with an owner and a due date

- **AC-10.1 (success):** **Given** I am a Project Manager and a member of project P, and Ana is a member of P, **when** I create a task in P titled "Draft homepage text" with owner Ana and due date 2026-11-06, **then** the task appears in P's task list with that title, owner Ana, due date 2026-11-06 and status "To do".
- **AC-10.2 (validation):** **Given** I am creating a task in P, **when** I leave the title empty and try to save, **then** the task is not created and a message states that a title is required.
- **AC-10.3 (validation):** **Given** Ben is a member of workspace W but not of project P, **when** a task in P is created or edited with Ben as its owner, **then** the request is refused and Ben does not become the owner (reworded 2026-11-17, see the decision log).
- **AC-10.4 (single owner):** **Given** a task in P has owner Ana, **when** I change its owner to Carla, who is a member of P, **then** Carla is shown as the task's only owner and Ana is no longer shown as its owner.
- **AC-10.5 (title rule):** **Given** I am creating a task in P, **when** I enter a title of only spaces, or a title shorter than 3 or longer than 150 characters after removing leading and trailing spaces, **then** the task is not created and a message states the rule. A title of exactly 3 or 150 characters is accepted (decided 2026-11-12).
- **Pending:** whether due date and description are required (PD-04). Whether Team Members may assign tasks to others (PD-01).

### US-11: Change a task's status

- **AC-11.1 (success):** **Given** I am a Team Member and the owner of task T, whose status is "To do", **when** I change T's status to "In progress", **then** T shows "In progress" in its project task list and in my "My tasks" list.
- **AC-11.2 (validation):** **Given** I am the owner of task T, **when** I open the list of statuses I can choose for T, **then** exactly three options are offered: "To do", "In progress" and "Done".
- **AC-11.3 (permission):** **Given** I am signed in as a Viewer, **when** I open task T, **then** no option to change its status is available, and T's status stays unchanged.
- **Pending:** status changes on tasks owned by someone else (PD-01). Moving a task from "Done" back to an earlier status (PD-06).

### US-13: See "My tasks" ordered by due date

- **AC-13.1 (success):** **Given** I am a Team Member who owns three tasks in two different projects, with due dates 2026-11-05, 2026-11-02 and 2026-11-09, **when** I open "My tasks", **then** all three tasks are listed in the order 2026-11-02, 2026-11-05, 2026-11-09.
- **AC-13.2 (exclusion):** **Given** a task in one of my projects is owned by Ben, **when** I open "My tasks", **then** that task is not listed.
- **AC-13.3 (empty state):** **Given** I own no tasks, **when** I open "My tasks", **then** the list contains no tasks and a message states that no tasks are assigned to me.
- **Pending:** whether "Done" tasks appear in "My tasks" (PD-06). Where tasks without a due date are placed (PD-08, see EC-06).

### US-15: Comment on a task

- **AC-15.1 (success):** **Given** I am a Team Member of project P, **when** I post the comment "The first two sections are done." on task T, **then** it appears last in T's comments with my name and the date and time it was posted, set by Matriz.
- **AC-15.2 (validation):** **Given** I am commenting on T, **when** I post a comment that is empty, only spaces, or longer than 2000 characters after removing leading and trailing spaces, **then** nothing is added and a message states the rule (EC-08). A comment of 1 or 2000 characters is accepted.
- **AC-15.3 (order and author):** **Given** T has comments by Ana and then Ben, **when** anyone in the workspace opens T, **then** Ana's comment is listed before Ben's, each with its author and date; a request that sends its own author or date is refused.
- **AC-15.4 (permission):** **Given** I am a Viewer, or not a member of P, **when** I open T, **then** I can read its comments but cannot add one. *(Provisional until PD-17 and PD-20 are decided. Workspace Administrators who are members of P can comment, decided 2026-11-19, PD-03 in part.)*

### US-17: Project progress indicator

- **AC-17.1 (success):** **Given** project P has 8 tasks, 2 of them with status "Done", **when** I view P in the project list, **then** its progress reads "2 of 8 tasks done".
- **AC-17.2 (update):** **Given** P shows "2 of 8 tasks done", **when** a third task in P is changed to "Done" and I then reopen or refresh the project list, **then** P shows "3 of 8 tasks done".
- **AC-17.3 (deletion):** **Given** P shows "3 of 8 tasks done", **when** a task in P with status "To do" is deleted and I then reopen or refresh the project list, **then** P shows "3 of 7 tasks done".
- **Pending:** whether a percentage is shown alongside the count, and how it is rounded (PD-07).

### US-18: Overdue highlighting

- **AC-18.1 (success):** **Given** today is 2026-11-10 and task T has due date 2026-11-09 and status "In progress", **when** I view T's project task list, **then** T is marked as overdue. The marking includes a text label or symbol and does not rely on colour alone (NFR-06).
- **AC-18.2 (boundary):** **Given** today is 2026-11-10 and task T has due date 2026-11-10 and status "To do", **when** I view the task list, **then** T is not marked as overdue.
- **AC-18.3 (exclusion):** **Given** today is 2026-11-10 and task T has due date 2026-11-01 and status "Done", **when** I view the task list, **then** T is not marked as overdue.
- **AC-18.4 (consistency):** **Given** task T is marked as overdue in its project task list, **when** T's owner opens "My tasks", **then** T is also marked as overdue there.
- **Pending:** which time zone decides when "today" begins (PD-08).

### US-19: Read-only access for Viewers

- **AC-19.1 (success):** **Given** I am signed in as a Viewer of workspace W, which has projects P1 and P2, **when** I open the project list, **then** both P1 and P2 are listed, whether or not I belong to either project.
- **AC-19.2 (permission):** **Given** I am a Viewer, **when** I open any project or task in W, **then** no options are available to create, edit, delete or archive projects or tasks, change a task's status or owner, or add a comment.
- **AC-19.3 (progress visible):** **Given** I am a Viewer and project P shows "2 of 8 tasks done" to its Project Manager, **when** I view P in the project list, **then** I see the same "2 of 8 tasks done".

### US-23: Project Manager invites a member

- **AC-23.1 (success):** **Given** I am signed in as a Project Manager of workspace W, **when** I enter the email address `ana@example.com`, which doesn't belong to any member of W, and send the invitation, **then** an invitation is delivered to `ana@example.com`, and after Ana accepts it she appears in W's member list.
- **Pending:** which roles a Project Manager may give an invitee (PD-02).

### US-24: Edit a project

- **AC-24.1 (success):** **Given** I am a Project Manager (or a Workspace Administrator, 2026-11-19) and project P has due date 2026-12-15, **when** I change P's due date to 2026-12-22 and save, **then** the project list shows P with due date 2026-12-22.
- **Pending:** whether archived projects can be edited (PD-05).

### US-25: Edit a task

- **AC-25.1 (validation):** **Given** I am editing task T, **when** I clear its title and try to save, **then** the change is not saved and a message states that a title is required (FR-09).
- **AC-25.2 (limits):** **Given** I am editing task T, **when** I enter a title of only spaces or a description longer than 2000 characters and try to save, **then** the change is not saved and a message states the rule (decided 2026-11-12).
- **Pending:** who may edit which tasks (PD-01); provisional rules are in openapi.yaml (`updateTask`).

### US-26: Delete a task I created

- **AC-26.1 (success):** **Given** I created task T in project P, **when** I delete T, **then** T no longer appears in P's task list. The effect on P's progress is covered by AC-17.3.
- **AC-26.2 (permission):** **Given** task T in project P was created by Ben, **when** I open T, **then** no option to delete T is available to me.
- **AC-26.3 (confirmation):** **Given** I created task T, which has 2 comments, **when** I choose to delete T, **then** I am asked to confirm and told that this cannot be undone; T is kept if I cancel, and if I confirm, T and its comments and notifications no longer exist (DA-05).

### US-27: Delete a project

- **AC-27.1 (confirmation):** **Given** I am a Project Manager (or a Workspace Administrator, 2026-11-19) of workspace W and project P in W has 3 tasks, **when** I choose to delete P, **then** I am asked to confirm, told that P and its 3 tasks will be deleted and that this cannot be undone, and P is not deleted if I cancel.
- **AC-27.2 (success):** **Given** I confirmed the deletion of P, **when** the project list is shown, **then** P no longer appears, and none of P's tasks, comments, notifications or project members remain (FR-06).
- **AC-27.3 (permission):** **Given** I am a Team Member or Viewer of W, **when** I open project P, **then** no option to delete P is available to me, and a deletion request from me is refused. *(Workspace Administrators are no longer refused: they can delete projects, like Project Managers, decided 2026-11-19, PD-03 in part.)*

### US-28: Search projects by name

- **AC-28.1 (match):** **Given** workspace W has the projects "Website redesign" and "Trade fair stand", **when** I search for "WEB", **then** the list shows "Website redesign" only.
- **AC-28.2 (no match):** **Given** no project in my workspaces has "zzz" in its name, **when** I search for "zzz", **then** the list is empty and a message states that no projects match.
- **AC-28.3 (pages):** **Given** my workspaces have more projects than fit on one page, **when** I open the project list, **then** I can move to the next and previous pages, and each project appears on exactly one page.

### US-29: Assign members to a task

- **AC-29.1 (several assignees):** **Given** I am a Project Manager and Ben and Vera are members of project P, **when** I assign both to task T, **then** T shows Ben and Vera as assignees, and T's owner is unchanged.
- **AC-29.2 (eligible only):** **Given** Carla is a member of the workspace but not of P, **when** I choose assignees for T, **then** Carla is not offered, and a request to assign her is refused.
- **AC-29.3 (no duplicates):** **Given** Ben is assigned to T, **when** I assign Ben to T again, **then** the request is refused with a message that Ben is already assigned, and Ben is listed once.
- **AC-29.4 (remove one):** **Given** Ben and Vera are assigned to T, **when** I remove Ben, **then** only Vera is listed.
- **AC-29.5 (permission):** **Given** I am a Team Member or Viewer, **when** I open T, **then** I see its assignees but no option to assign or remove. *(Provisional until PD-01 is decided. Workspace Administrators assign and remove too, decided 2026-11-19, PD-03 in part.)*

## Relevant edge cases and expected behavior

| Case | Related story | Expected behavior |
|---|---|---|
| **EC-01** An invitation is sent to an email address that already belongs to a member of the workspace | US-03 | No invitation is sent, no duplicate member is created, and a message states that this person is already a member. |
| **EC-02** An invitation is sent to an address that already has a pending, unaccepted invitation | US-03 | No duplicate member is created when the person accepts. Resending and expiry are pending (PD-09). |
| **EC-03** The only Workspace Administrator tries to deactivate themselves or change their own role | US-04, US-05 | The change is refused and a message states that the workspace must keep at least one Workspace Administrator. |
| **EC-04** A member who owns open tasks is deactivated | US-05, US-10 | The tasks are not deleted. They keep their comments and stay in the project task list and in the progress count (NFR-03). How the owner is shown and who reassigns the tasks is pending (PD-10). |
| **EC-05** A project has no tasks yet | US-17 | Progress reads "0 of 0 tasks done". No percentage or error is shown. |
| **EC-06** A task has no due date (if due dates turn out to be optional, PD-04) | US-13, US-18 | The task is never marked as overdue. Its position in "My tasks" is pending (PD-08). |
| **EC-07** Two people change the status of the same task within seconds of each other | US-11 | The task ends up in exactly one of the three statuses, and every user sees that same status after refreshing. Which change wins is pending (PD-11). |
| **EC-08** A Team Member tries to post an empty comment, or one containing only spaces | US-15 | The comment is not posted and nothing is added to the task (FR-16). |
| **EC-09** A Project Manager removes a person from a project while that person owns tasks in it | US-08, US-10 | Pending (PD-10). Either the removal is refused until the tasks are reassigned, or the tasks are kept and flagged for reassignment. In either case, no task is deleted (NFR-03). |
| **EC-10** An assignee is deactivated, or two Project Managers assign the same member at the same moment | US-29 | A deactivated assignee stays listed (as for owners, EC-04) but is no longer offered. Of two simultaneous assignments of the same member, only one is stored; the other is refused as a duplicate. |

## Traceability

| Story | Functional requirement(s) | Source feature(s) | Priority |
|---|---|---|---|
| US-01 | FR-01 | F01 | Must have |
| US-02 | FR-02 | F02 | Must have |
| US-03 | FR-03, FR-04 | F03, F04 | Must have |
| US-04 | FR-04 | F04 | Must have |
| US-05 | FR-03 | F03 | Must have |
| US-06 | FR-06 | F09 | Must have |
| US-07 | FR-06 | F09 | Must have |
| US-08 | FR-07 | F10 | Must have |
| US-09 | FR-08 | F11 | Must have |
| US-10 | FR-09, FR-10, FR-11 | F13, F14, F15 | Must have |
| US-11 | FR-12 | F16 | Must have |
| US-12 | FR-13 | F24 | Must have |
| US-13 | FR-14 | F25 | Must have |
| US-14 | FR-15 | F26 | Should have |
| US-15 | FR-16 | F31 | Must have |
| US-16 | FR-17 | F36 | Must have |
| US-17 | FR-18 | F39 | Must have |
| US-18 | FR-19 | F40 | Must have |
| US-19 | FR-05, FR-08 | F05, F11 | Should have |
| US-20 | FR-20 | F44 | Should have |
| US-21 | FR-21 | F45 | Must have |
| US-22 | FR-01 | F01 | Must have |
| US-23 | FR-03 | F03 | Must have |
| US-24 | FR-06 | F09 | Must have |
| US-25 | FR-09 | F13 | Must have |
| US-26 | FR-09 | F13 | Must have |
| US-27 | FR-06 | F09 | Must have |
| US-28 | FR-08 | F11, F27 (project name only) | Must have |
| US-29 | FR-22, FR-07 | F14 | Must have |

Every functional requirement from FR-01 to FR-22 is covered by at least one story, including sign-up (US-22), invitations by Project Managers (US-23), project editing and deletion (US-24, US-27), project search (US-28), task editing and deletion (US-25, US-26) and task assignees (US-29).
