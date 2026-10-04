-- Interview feedback (scorecards, INT-24…): one per panel member per interview. Staff only;
-- panel members see each other's feedback only after submitting their own.
CREATE TABLE interview_feedback (
    id BINARY(16) NOT NULL PRIMARY KEY,
    interview_id BINARY(16) NOT NULL,
    author_email VARCHAR(254) NOT NULL,
    author_name VARCHAR(200),
    attendance VARCHAR(30) NOT NULL,
    ratings_json TEXT,
    strengths TEXT,
    concerns TEXT,
    questions_asked TEXT,
    recommendation VARCHAR(20),
    notes TEXT,
    submitted_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_interview_feedback_interview FOREIGN KEY (interview_id) REFERENCES interview (id),
    CONSTRAINT uk_interview_feedback_author UNIQUE (interview_id, author_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
