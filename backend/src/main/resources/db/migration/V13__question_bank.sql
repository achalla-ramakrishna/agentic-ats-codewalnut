-- Question bank (ADR-0014): reusable questions tagged by area (aptitude now; Java, Python … later),
-- section, topic and difficulty, with optional pictures. Test papers copy questions from it.
CREATE TABLE bank_question (
    id BINARY(16) NOT NULL PRIMARY KEY,
    area VARCHAR(20) NOT NULL,
    section VARCHAR(20) NOT NULL,
    topic VARCHAR(60) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    prompt TEXT NOT NULL,
    code TEXT,
    figure MEDIUMTEXT,
    options_json TEXT,
    option_figures_json MEDIUMTEXT,
    answer_json TEXT NOT NULL,
    points INT NOT NULL,
    explanation TEXT,
    source VARCHAR(20) NOT NULL,
    builtin_key VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    times_used INT NOT NULL DEFAULT 0,
    created_by VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_bank_question_builtin UNIQUE (builtin_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_bank_question_filter ON bank_question (area, section, difficulty, status);

-- Pictures and tags on test questions (copied from the bank, or added by hand).
ALTER TABLE assessment_question
    ADD COLUMN figure MEDIUMTEXT,
    ADD COLUMN option_figures_json MEDIUMTEXT,
    ADD COLUMN section VARCHAR(20),
    ADD COLUMN topic VARCHAR(60),
    ADD COLUMN difficulty VARCHAR(10),
    ADD COLUMN bank_question_id BINARY(16);
