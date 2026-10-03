-- New test results (ASMT-32): a submitted test stays "new" until someone who manages tests opens
-- its answers or marks it seen.
ALTER TABLE assessment_invite
    ADD COLUMN reviewed_at DATETIME(6),
    ADD COLUMN reviewed_by VARCHAR(254);

CREATE INDEX idx_assessment_invite_status_reviewed ON assessment_invite (status, reviewed_at);
