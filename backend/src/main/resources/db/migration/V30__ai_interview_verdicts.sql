-- A person's verdict on a completed AI interview (AINT-21): select for the next round, hold, or don't
-- progress, with feedback and optional marks per answer. One per reviewer per interview; they can change it.
CREATE TABLE ai_interview_verdict (
    id BINARY(16) NOT NULL PRIMARY KEY,
    invite_id BINARY(16) NOT NULL,
    reviewer_email VARCHAR(254) NOT NULL,
    reviewer_name VARCHAR(200) NULL,
    decision VARCHAR(20) NOT NULL,
    -- [{position, mark (GOOD|OK|WEAK), note}]
    marks_json TEXT NULL,
    feedback TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_ai_verdict UNIQUE (invite_id, reviewer_email),
    CONSTRAINT fk_ai_verdict_invite FOREIGN KEY (invite_id) REFERENCES assessment_invite (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
