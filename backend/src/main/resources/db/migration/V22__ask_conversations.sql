-- Ask ATS (ASK-01…, ADR-0021): each staff member's own chats with the ATS assistant.
-- Only questions and answers are kept; lookups are re-run on each question.
CREATE TABLE ask_conversation (
    id BINARY(16) NOT NULL PRIMARY KEY,
    owner_email VARCHAR(254) NOT NULL,
    title VARCHAR(120) NOT NULL,
    messages_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX ix_ask_conversation_owner (owner_email, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
