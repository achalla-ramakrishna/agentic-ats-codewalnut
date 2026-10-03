-- Résumé intelligence (ADR-0010): bulk résumé uploads waiting to be read, and the AI's reading of
-- each candidate's résumé against the opening. Both are advisory; people make every decision.

-- A résumé uploaded in bulk to an opening. The file is kept only until it has been read and
-- attached to a candidate; then data is cleared.
CREATE TABLE resume_intake (
    id BINARY(16) NOT NULL PRIMARY KEY,
    job_id BINARY(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data LONGBLOB,
    status VARCHAR(20) NOT NULL,
    outcome VARCHAR(30),
    application_id BINARY(16),
    error VARCHAR(500),
    uploaded_by VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6),
    CONSTRAINT fk_resume_intake_job FOREIGN KEY (job_id) REFERENCES job_opening (id),
    CONSTRAINT fk_resume_intake_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_resume_intake_job ON resume_intake (job_id, created_at);
CREATE INDEX idx_resume_intake_status ON resume_intake (status);

-- The AI's reading of one candidate's résumé for one opening. data holds the structured
-- profile as JSON; fit_percent is computed by the app from the per-requirement assessment.
CREATE TABLE candidate_insight (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    document_id BINARY(16),
    fit_percent INT,
    headline VARCHAR(300),
    data MEDIUMTEXT,
    error VARCHAR(500),
    model VARCHAR(100),
    job_hash VARCHAR(64),
    analyzed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_candidate_insight_application UNIQUE (application_id),
    CONSTRAINT fk_candidate_insight_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_candidate_insight_status ON candidate_insight (status);
