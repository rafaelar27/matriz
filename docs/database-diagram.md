# Matriz — ER Diagram

> Diagram of the MVP data model. Entities, attributes, keys and cardinalities are defined in [database.md](database.md); relationship labels match its R-xx IDs. If the two ever differ, database.md is authoritative and this diagram must be updated.

```mermaid
erDiagram
    Account ||--o{ Member : "R-01 holds"
    Workspace ||--|{ Member : "R-02 has"
    Workspace ||--o{ Invitation : "R-03 receives"
    Member ||--o{ Invitation : "R-04 sends"
    Workspace ||--o{ Project : "R-05 contains"
    Member ||--o{ Project : "R-06 owns"
    Project ||--o{ ProjectMember : "R-07 includes"
    Member ||--o{ ProjectMember : "R-08 takes part as"
    Project ||--o{ Task : "R-09 contains"
    Member ||--o{ Task : "R-10 owns"
    Member ||--o{ Task : "R-11 creates"
    Task ||--o{ Comment : "R-12 has"
    Member ||--o{ Comment : "R-13 writes"
    Member ||--o{ Notification : "R-14 receives"
    Task ||--o{ Notification : "R-15 concerns"
    Comment |o--o| Notification : "R-16 triggers"
    Member ||--o{ Feedback : "R-17 sends"
    Account ||--o{ PersonalDataRequest : "R-18 makes"

    Account {
        id account_id PK
        string name "personal data"
        string email UK "personal data"
    }
    Workspace {
        id workspace_id PK
        string name
    }
    Member {
        id member_id PK
        id workspace_id FK, UK "unique with account_id"
        id account_id FK, UK "unique with workspace_id"
        enum role "4 roles"
        enum status "active, deactivated"
    }
    Invitation {
        id invitation_id PK
        id workspace_id FK
        string email "personal data"
        enum role "4 roles"
        id invited_by_member_id FK
        enum status "pending, accepted"
    }
    Project {
        id project_id PK
        id workspace_id FK
        string name
        text description "optional"
        id owner_member_id FK
        date start_date "optional, not after due_date"
        date due_date "optional"
        boolean is_archived
    }
    ProjectMember {
        id project_id PK, FK
        id member_id PK, FK
    }
    Task {
        id task_id PK
        id project_id FK
        string title
        text description "optional"
        id owner_member_id FK
        date due_date "optional"
        enum status "To do, In progress, Done"
        id created_by_member_id FK
    }
    Comment {
        id comment_id PK
        id task_id FK
        id author_member_id FK
        text body
        datetime created_at
    }
    Notification {
        id notification_id PK
        id recipient_member_id FK
        enum type "task assigned, comment added"
        id task_id FK
        id comment_id FK "optional"
        datetime created_at
    }
    Feedback {
        id feedback_id PK
        id member_id FK
        text message
        datetime created_at
    }
    PersonalDataRequest {
        id request_id PK
        id account_id FK
        enum type "copy, deletion"
        enum status "received, completed"
        datetime requested_at
        datetime completed_at "optional"
    }
```
