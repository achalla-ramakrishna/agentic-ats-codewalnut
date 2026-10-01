-- Richer candidate profile for onboarding and background verification (BGV).
-- No Aadhaar or PAN numbers are stored as fields; only the (masked) documents.
ALTER TABLE candidate
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN current_address VARCHAR(1000),
    ADD COLUMN permanent_address VARCHAR(1000),
    ADD COLUMN college VARCHAR(200),
    ADD COLUMN degree VARCHAR(200),
    ADD COLUMN graduation_year INT,
    ADD COLUMN linkedin_url VARCHAR(300),
    ADD COLUMN emergency_contact VARCHAR(300),
    ADD COLUMN profile_updated_at DATETIME(6);

-- Documents staff asked a candidate to upload from their candidate page.
CREATE TABLE document_request (
    id BINARY(16) NOT NULL PRIMARY KEY,
    candidate_id BINARY(16) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    requested_by VARCHAR(254) NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    fulfilled_at DATETIME(6),
    CONSTRAINT fk_document_request_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_document_request_candidate ON document_request (candidate_id, kind);

-- People at a client (e.g. Blend's hiring manager) who may sign in and see what was shared with
-- their company. Deactivated rather than deleted, so the audit trail stays readable.
CREATE TABLE client_contact (
    id BINARY(16) NOT NULL PRIMARY KEY,
    client_id BINARY(16) NOT NULL,
    email VARCHAR(254) NOT NULL,
    name VARCHAR(200),
    active BIT(1) NOT NULL DEFAULT b'1',
    added_by VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at DATETIME(6),
    CONSTRAINT uk_client_contact_email UNIQUE (email),
    CONSTRAINT fk_client_contact_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- What CodeWalnut chose to share with the client about one application. One row per
-- application; revoking keeps the row (revoked_at) for the record.
CREATE TABLE client_share (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    client_id BINARY(16) NOT NULL,
    include_contact BIT(1) NOT NULL DEFAULT b'0',
    include_profile BIT(1) NOT NULL DEFAULT b'0',
    note VARCHAR(1000),
    shared_by VARCHAR(254) NOT NULL,
    shared_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at DATETIME(6),
    last_viewed_at DATETIME(6),
    CONSTRAINT uk_client_share_application UNIQUE (application_id),
    CONSTRAINT fk_client_share_application FOREIGN KEY (application_id) REFERENCES application (id),
    CONSTRAINT fk_client_share_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_client_share_client ON client_share (client_id, revoked_at);

CREATE TABLE client_share_document (
    share_id BINARY(16) NOT NULL,
    document_id BINARY(16) NOT NULL,
    PRIMARY KEY (share_id, document_id),
    CONSTRAINT fk_client_share_document_share FOREIGN KEY (share_id) REFERENCES client_share (id),
    CONSTRAINT fk_client_share_document_document FOREIGN KEY (document_id) REFERENCES candidate_document (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
