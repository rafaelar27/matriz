-- Deleting a project deletes its dependent records (US-27; decided by the project decision-maker on 2026-11-12,
-- recorded in docs/database.md and the decision log in docs/constraints.md):
--   project -> project_member   (cascade, new)
--   project -> task             (cascade, new)
--   task    -> comment          (cascade, V1, DA-05)
--   task    -> notification     (cascade, V1, DA-05)
-- So no orphan tasks, comments, notifications or project members remain. All other foreign keys are unchanged.

ALTER TABLE project_member
    DROP CONSTRAINT project_member_project_id_fkey,
    ADD CONSTRAINT project_member_project_id_fkey
        FOREIGN KEY (project_id) REFERENCES project (project_id) ON DELETE CASCADE;

ALTER TABLE task
    DROP CONSTRAINT task_project_id_fkey,
    ADD CONSTRAINT task_project_id_fkey
        FOREIGN KEY (project_id) REFERENCES project (project_id) ON DELETE CASCADE;
