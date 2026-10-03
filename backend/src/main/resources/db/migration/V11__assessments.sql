-- Online tests (aptitude, Java, Python …) that CodeWalnut writes and sends to candidates
-- (docs/features/assessments.md, ADR-0011). Scored automatically; people decide.

CREATE TABLE assessment (
    id BINARY(16) NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(20) NOT NULL,
    description TEXT,
    duration_minutes INT NOT NULL,
    pass_percent INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A question. options_json: the choices (choice questions); answer_json: the correct choice
-- indexes, or the accepted answers for a short answer. Never sent to candidates.
CREATE TABLE assessment_question (
    id BINARY(16) NOT NULL PRIMARY KEY,
    assessment_id BINARY(16) NOT NULL,
    position INT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    prompt TEXT NOT NULL,
    code TEXT,
    options_json TEXT,
    answer_json TEXT NOT NULL,
    points INT NOT NULL,
    explanation TEXT,
    ai_drafted BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_assessment_question_assessment FOREIGN KEY (assessment_id) REFERENCES assessment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_assessment_question_assessment ON assessment_question (assessment_id, position);

-- A test sent to one candidate for one opening. answers_json: the candidate's answers by
-- question id, saved as they go.
CREATE TABLE assessment_invite (
    id BINARY(16) NOT NULL PRIMARY KEY,
    assessment_id BINARY(16) NOT NULL,
    application_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_by VARCHAR(254) NOT NULL,
    sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    due_at DATETIME(6) NOT NULL,
    started_at DATETIME(6),
    deadline_at DATETIME(6),
    submitted_at DATETIME(6),
    score INT,
    max_score INT,
    percent INT,
    answers_json MEDIUMTEXT,
    reminder_count INT NOT NULL DEFAULT 0,
    last_reminded_at DATETIME(6),
    CONSTRAINT fk_assessment_invite_assessment FOREIGN KEY (assessment_id) REFERENCES assessment (id),
    CONSTRAINT fk_assessment_invite_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_assessment_invite_application ON assessment_invite (application_id);
CREATE INDEX idx_assessment_invite_assessment ON assessment_invite (assessment_id);
