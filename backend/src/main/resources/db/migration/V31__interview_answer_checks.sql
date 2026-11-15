-- Reading checks for AI interviews (AINT-24, ADR-0031): spoken questions stay hidden until the
-- candidate opens them, each answer can be recorded at most twice, and each answer carries
-- advisory signals plus an AI check of whether it sounds read out, done as soon as it uploads.
CREATE TABLE interview_answer_check (
    id BINARY(16) NOT NULL PRIMARY KEY,
    invite_id BINARY(16) NOT NULL,
    question_id BINARY(16) NOT NULL,
    -- When the candidate first saw the question.
    revealed_at TIMESTAMP(6) NOT NULL,
    -- Recordings uploaded (1 = first take, 2 = one retake).
    takes INT NOT NULL DEFAULT 0,
    -- Seconds from seeing the question to starting the first take (worked out by the server).
    think_seconds INT NULL,
    -- Times the candidate left the window or tab while recording (all takes).
    focus_losses INT NOT NULL DEFAULT 0,
    -- The AI's reading check of the latest take: PENDING, LIKELY, POSSIBLE, UNLIKELY, SKIPPED or FAILED.
    reading VARCHAR(20) NULL,
    reading_reason VARCHAR(600) NULL,
    checked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_answer_check UNIQUE (invite_id, question_id),
    CONSTRAINT fk_answer_check_invite FOREIGN KEY (invite_id) REFERENCES assessment_invite (id) ON DELETE CASCADE
);

-- When the team was told this candidate may be reading, so they are told once per interview.
ALTER TABLE assessment_invite ADD COLUMN reading_alerted_at TIMESTAMP(6) NULL;
