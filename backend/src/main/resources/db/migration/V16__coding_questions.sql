-- Coding questions (ADR-0016): candidates write code that runs against test cases in a sandbox
-- (self-hosted Judge0). The public part (statement extras, samples, starter code, limits) lives
-- in coding_json; hidden test cases live in answer_json, which never reaches candidates.
ALTER TABLE assessment_question ADD COLUMN coding_json MEDIUMTEXT;
ALTER TABLE bank_question ADD COLUMN coding_json MEDIUMTEXT;
-- answer_json holds hidden test cases for coding questions, which can be larger than TEXT.
ALTER TABLE assessment_question MODIFY answer_json MEDIUMTEXT NOT NULL;
ALTER TABLE bank_question MODIFY answer_json MEDIUMTEXT NOT NULL;

-- Code is graded after submit by a background worker; the score is final once grading is DONE.
ALTER TABLE assessment_invite
    ADD COLUMN grading VARCHAR(20),
    ADD COLUMN grading_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN code_results_json MEDIUMTEXT,
    ADD COLUMN activity_json TEXT;
CREATE INDEX idx_assessment_invite_grading ON assessment_invite (grading);
