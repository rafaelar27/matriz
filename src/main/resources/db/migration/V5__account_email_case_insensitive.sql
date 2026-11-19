-- INC-12 public sign-up (decided by the project decision-maker on 2026-11-18; decision log in docs/constraints.md,
-- docs/implementation-plan.md INC-12, US-22):
--   BR-24 / DA-01: one account per email, compared without regard to case. The database enforces it, not only the code.
-- Precondition: no two accounts have emails that differ only by case. The project decision-maker resolves any such
-- pair beforehand. This migration changes no rows (it does not lower-case stored emails; the application normalizes
-- emails in code), needs no extension, and fails as a whole (PostgreSQL transactional DDL) and changes nothing if the
-- precondition is not met. account_email_uk (V1) stays.

CREATE UNIQUE INDEX account_email_lower_uk ON account (lower(email));
