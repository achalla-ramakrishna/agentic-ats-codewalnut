-- A candidate's sign-in identity (any verified Google account; email magic link later).
-- Separate from staff AppUsers; linked to candidate profiles and applications in later chunks.
CREATE TABLE candidate_account (
    id BINARY(16) NOT NULL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    name VARCHAR(200),
    google_subject VARCHAR(255),
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_candidate_account_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
