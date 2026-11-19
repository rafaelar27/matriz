-- INC-17 invitations (decided by the project decision-maker on 2026-11-19; decision QK):
--   * invitation.created_at records when an invitation was made (docs/database.md, Invitation).
--   * A cancelled invitation keeps its row with status CANCELLED. Closing the other PENDING invitations for the same
--     address and workspace when one is accepted is a cross-row rule kept by the invitations module, not here.
--   * Indexes: a workspace's pending invitations, and "my invitations" by email. Emails follow BR-24 (stored in lower
--     case by the module); the index uses lower(email) so lookups match whatever case was stored. Not unique:
--     several invitations to one address are allowed.
-- Precondition: the human checks the development database for existing invitation rows (no code wrote them before
-- INC-17). Rows already present would all get this migration's apply time as created_at. No other row changes.

ALTER TABLE invitation
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE invitation
    DROP CONSTRAINT invitation_status_ck,
    ADD CONSTRAINT invitation_status_ck CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED'));

CREATE INDEX invitation_workspace_status_idx ON invitation (workspace_id, status);
CREATE INDEX invitation_email_lower_idx ON invitation (lower(email), status);
