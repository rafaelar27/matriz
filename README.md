# Matriz

## Presentation of the Matriz project

Matriz is a project and task management platform designed for work teams. It covers project and task management and progress tracking (known facts KF-01 to KF-06 in [docs/requirements.md](docs/requirements.md#known-facts)).

This repository currently holds the product documentation that serves as the single source of truth for future design and development.

## Problem it aims to solve

*Assumption (AQ1, AQ5, AQ14):* teams lose track of who is doing what and by when, because their work is scattered across email, chat and spreadsheets, or sits in project tools they find too complex. The result is unclear ownership, lost updates, and no single overview of status.

## Main objectives

Full definitions and their basis are in [docs/requirements.md](docs/requirements.md#objectives).

- **OBJ-01:** one place to organize team work.
- **OBJ-02:** an explicit owner and due date for every task.
- **OBJ-03:** visibility of progress and delays for managers and stakeholders.
- **OBJ-04:** simplicity, so that Matriz is usable without training.
- **OBJ-05:** validation with pilot teams before a wider launch.

## Features planned for the MVP

The MVP serves four user types: Workspace Administrator, Project Manager, Team Member and Viewer. It includes 21 functional requirements: 18 Must have and 3 Should have. Details are in [docs/requirements.md](docs/requirements.md#functional-requirements).

| Area | Requirements |
|---|---|
| Access and workspace | FR-01 Sign up and sign in · FR-02 Workspace creation · FR-03 Invite and remove members · FR-04 Roles and permissions · FR-05 Read-only access |
| Projects | FR-06 Create, edit and archive projects · FR-07 Project members · FR-08 Project list |
| Tasks | FR-09 Create, edit and delete tasks · FR-10 Assign a responsible person · FR-11 Due dates · FR-12 Task status |
| Views | FR-13 Project task list · FR-14 "My tasks" · FR-15 Filter tasks |
| Collaboration and notifications | FR-16 Task comments · FR-17 In-app notifications |
| Progress tracking | FR-18 Project progress indicator · FR-19 Overdue highlighting |
| Support and privacy | FR-20 Send feedback · FR-21 Personal data requests |

## Documentation structure

| File | Contents |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Vision and objectives, user types, functional requirements (FR-xx), non-functional requirements (NFR-xx), MVP scope, features outside the initial scope |
| [docs/user-stories.md](docs/user-stories.md) | User stories (US-xx), priorities, acceptance criteria, edge cases (EC-xx), traceability to requirements |
| [docs/constraints.md](docs/constraints.md) | Known constraints (KC-xx), assumptions (AQ1–AQ33), security and privacy requirements (SP-xx), pending technical decisions (TD-xx), scope limitations and pending product decisions (PD-xx) |
| [docs/architecture.md](docs/architecture.md) | Architectural style (decided 2026-11-05), components (CMP-xx), requirement coverage, component diagram |
| [docs/database.md](docs/database.md) | Conceptual and logical data model, business rules (BR-xx), data-model assumptions (DA-xx) |
| [docs/database-diagram.md](docs/database-diagram.md) | Entity-relationship diagram of the data model |
| [openapi.yaml](openapi.yaml) | Draft API specification for task creation and task status changes, API assumptions (API-Ax) |
| [docs/ui/README.md](docs/ui/README.md) | UI notes for the dashboard: screen elements (E-xx), navigation, UI assumptions (UI-Ax) |
| [docs/ui/dashboard-wireframe.html](docs/ui/dashboard-wireframe.html) | Low-fidelity dashboard wireframe |

## Current project status

- **Stage:** design, in draft. The specification documents remain drafts pending client validation.
- **Done:** initial analysis, functional proposal and MVP specifications. Architectural style decided on 2026-11-05 (modular monolith, see the [decision log](docs/constraints.md#decision-log)). Draft data model, ER diagram, API specification for two operations, and dashboard wireframe.
- **Not started:** technology choices and development. No technology has been chosen; see [pending technical decisions](docs/constraints.md#pending-technical-decisions) (TD-01 to TD-12).
- **Blocking next step:** the five assumptions that shape the MVP most (AQ3, AQ6, AQ7, AQ11, AQ17) were confirmed on 2026-10-29 (see the [decision log](docs/constraints.md#decision-log)). The remaining [pending product decisions](docs/constraints.md#pending-product-decisions) (PD-01 to PD-13 and PD-15 to PD-24) must be made before the features they affect are designed. While the client is unavailable, the project decision-maker makes them (KC-01).
- **Last updated:** 2026-11-05.
