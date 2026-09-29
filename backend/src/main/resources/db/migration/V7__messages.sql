-- Conversations per application: CANDIDATE (staff <-> candidate, optionally emailed) and
-- TEAM (internal, never shown to the candidate). Append-only.
CREATE TABLE message (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    author_type VARCHAR(20) NOT NULL,
    author_email VARCHAR(254) NOT NULL,
    author_name VARCHAR(200),
    subject VARCHAR(300),
    body TEXT NOT NULL,
    emailed BIT(1) NOT NULL DEFAULT b'0',
    email_message_id VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_message_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_message_thread ON message (application_id, channel, created_at);
CREATE INDEX idx_message_channel_created ON message (channel, created_at);

-- When the candidate last opened their messages, for "new message" badges.
ALTER TABLE application ADD COLUMN candidate_read_at DATETIME(6);
