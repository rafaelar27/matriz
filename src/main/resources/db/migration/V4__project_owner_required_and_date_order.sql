-- INC-07 (decided by the project decision-maker on 2026-11-12; decision log in docs/constraints.md,
-- docs/implementation-plan.md INC-07):
--   D-QF2: a project always has an owner; an empty owner is refused (DA-03 owner part rejected; R-06 becomes 1 to 0..*).
--   D-QF1 / QG3: a project's start date is not after its due date when both are given; either date may be empty (DA-03).
-- Precondition: no project has owner_member_id IS NULL and none has start_date > due_date. The project
-- decision-maker names the owners and corrects the dates beforehand (QG1 b, QG3). This migration changes no rows,
-- invents no owner or date, and fails as a whole (PostgreSQL transactional DDL) if the precondition is not met.
-- The foreign key to member is unchanged (NO ACTION). Rules that span rows (owner active, same workspace: BR-18)
-- stay in ProjectService.

ALTER TABLE project
    ALTER COLUMN owner_member_id SET NOT NULL;

ALTER TABLE project
    ADD CONSTRAINT project_date_order_ck
        CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date);
