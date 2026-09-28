-- Résumés per candidate (original and CodeWalnut-formatted). Stored in MySQL for now; move to
-- object storage when volume grows. Old versions are kept; the newest per kind is current.
CREATE TABLE candidate_document (
    id BINARY(16) NOT NULL PRIMARY KEY,
    candidate_id BINARY(16) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data LONGBLOB NOT NULL,
    uploaded_by VARCHAR(254),
    uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_candidate_document_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_candidate_document_candidate ON candidate_document (candidate_id, kind, uploaded_at);
