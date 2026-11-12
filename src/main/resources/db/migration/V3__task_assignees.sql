-- INC-05 (decided 2026-11-12): members assigned to a task in addition to its single owner (FR-22, US-29, BR-22).
-- The primary key makes a second assignment of the same member to the same task impossible.
-- Deleting a task deletes its assignments (no orphans); a member row is never deleted in the MVP.
CREATE TABLE task_assignee (
    task_id     UUID        NOT NULL REFERENCES task (task_id) ON DELETE CASCADE,
    member_id   UUID        NOT NULL REFERENCES member (member_id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT task_assignee_pk PRIMARY KEY (task_id, member_id)
);

CREATE INDEX task_assignee_member_idx ON task_assignee (member_id);
