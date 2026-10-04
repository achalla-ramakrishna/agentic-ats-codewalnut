-- Feedback drafts (INT-35): interviewers fill the form during the interview; a draft is private
-- to its author until submitted.
ALTER TABLE interview_feedback ADD COLUMN draft BOOLEAN NOT NULL DEFAULT FALSE;
