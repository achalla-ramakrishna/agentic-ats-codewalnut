-- AI interviews (ADR-0030): a test of kind INTERVIEW whose questions are grouped into phases
-- (section) and may be answered on video. Videos live on disk (a Railway volume), not in MySQL.

-- How long a video answer may be, in seconds (VIDEO questions only).
ALTER TABLE assessment_question ADD COLUMN max_seconds INT NULL;

-- The advisory AI review of a submitted interview: PENDING → DONE (or FAILED after retries).
ALTER TABLE assessment_invite
    ADD COLUMN evaluation VARCHAR(20) NULL,
    ADD COLUMN evaluation_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN evaluation_json MEDIUMTEXT NULL,
    ADD COLUMN ai_score INT NULL,
    ADD COLUMN evaluated_at TIMESTAMP(6) NULL;

-- One recorded answer per question (recording again replaces it).
CREATE TABLE interview_video (
    id BINARY(16) NOT NULL PRIMARY KEY,
    invite_id BINARY(16) NOT NULL,
    question_id BINARY(16) NOT NULL,
    storage_key VARCHAR(200) NOT NULL,
    content_type VARCHAR(60) NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_seconds INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_video_answer UNIQUE (invite_id, question_id),
    CONSTRAINT fk_video_invite FOREIGN KEY (invite_id) REFERENCES assessment_invite (id) ON DELETE CASCADE
);
