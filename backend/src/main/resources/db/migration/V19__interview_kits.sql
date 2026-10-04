-- Interview kits (INT-28…): one per opening, generated from its job description. Staff only.
CREATE TABLE interview_kit (
    id BINARY(16) NOT NULL PRIMARY KEY,
    job_id BINARY(16) NOT NULL,
    kit_json MEDIUMTEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    generated_by VARCHAR(254) NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_interview_kit_job FOREIGN KEY (job_id) REFERENCES job_opening (id),
    CONSTRAINT uk_interview_kit_job UNIQUE (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
