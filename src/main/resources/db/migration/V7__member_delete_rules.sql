-- INC-18 permanent removal of a member, replacing deactivation (decided 2026-11-19; decisions QA, QB, QC, QD, QF, QR):
--   Content the member made is kept and shown as "Former member": the reference becomes empty (ON DELETE SET NULL),
--   so the four columns become nullable.
--     task.created_by_member_id, comment.author_member_id, invitation.invited_by_member_id, feedback.member_id
--   Records that only make sense with the member are removed with it (ON DELETE CASCADE):
--     task_assignee.member_id (unassign), project_member.member_id (QD), notification.recipient_member_id (QF)
--   Unchanged, NO ACTION: task.owner_member_id and project.owner_member_id stay NOT NULL. The application reassigns
--   ownership to another Workspace Administrator before it deletes the member (QB, QC, self-removal); the database
--   refuses the delete otherwise.
-- No existing row changes. Constraint names are the PostgreSQL defaults from the inline REFERENCES in V1 and V3.

ALTER TABLE task
    ALTER COLUMN created_by_member_id DROP NOT NULL,
    DROP CONSTRAINT task_created_by_member_id_fkey,
    ADD CONSTRAINT task_created_by_member_id_fkey
        FOREIGN KEY (created_by_member_id) REFERENCES member (member_id) ON DELETE SET NULL;

ALTER TABLE comment
    ALTER COLUMN author_member_id DROP NOT NULL,
    DROP CONSTRAINT comment_author_member_id_fkey,
    ADD CONSTRAINT comment_author_member_id_fkey
        FOREIGN KEY (author_member_id) REFERENCES member (member_id) ON DELETE SET NULL;

ALTER TABLE invitation
    ALTER COLUMN invited_by_member_id DROP NOT NULL,
    DROP CONSTRAINT invitation_invited_by_member_id_fkey,
    ADD CONSTRAINT invitation_invited_by_member_id_fkey
        FOREIGN KEY (invited_by_member_id) REFERENCES member (member_id) ON DELETE SET NULL;

ALTER TABLE feedback
    ALTER COLUMN member_id DROP NOT NULL,
    DROP CONSTRAINT feedback_member_id_fkey,
    ADD CONSTRAINT feedback_member_id_fkey
        FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE SET NULL;

ALTER TABLE task_assignee
    DROP CONSTRAINT task_assignee_member_id_fkey,
    ADD CONSTRAINT task_assignee_member_id_fkey
        FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE;

ALTER TABLE project_member
    DROP CONSTRAINT project_member_member_id_fkey,
    ADD CONSTRAINT project_member_member_id_fkey
        FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE CASCADE;

ALTER TABLE notification
    DROP CONSTRAINT notification_recipient_member_id_fkey,
    ADD CONSTRAINT notification_recipient_member_id_fkey
        FOREIGN KEY (recipient_member_id) REFERENCES member (member_id) ON DELETE CASCADE;
