-- Admin updates (ADM-14…): an FYI for admins when interview feedback comes in or a candidate
-- reaches an important stage. Kept in the app; also emailed to admins when the person who
-- made the change has connected Gmail.
CREATE TABLE admin_update (
    id BINARY(16) NOT NULL PRIMARY KEY,
    kind VARCHAR(30) NOT NULL,
    application_id BINARY(16) NOT NULL,
    interview_id BINARY(16),
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    actor_email VARCHAR(254) NOT NULL,
    email_status VARCHAR(20) NOT NULL,
    emailed_to TEXT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_admin_update_application FOREIGN KEY (application_id) REFERENCES application (id),
    INDEX ix_admin_update_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
