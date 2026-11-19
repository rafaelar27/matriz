-- INC-19 soft delete of a workspace (decided 2026-11-19; decision QO: kept indefinitely, no purge rule, no index).
-- deleted_at is empty while the workspace is active. No row changes. Filtering on it is the module's job.

ALTER TABLE workspace
    ADD COLUMN deleted_at TIMESTAMPTZ;
