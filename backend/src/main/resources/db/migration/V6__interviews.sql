-- Interviews scheduled on the organiser's Google Calendar, with a Meet link.
CREATE TABLE interview (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    interviewer_emails VARCHAR(2600),
    message TEXT,
    status VARCHAR(20) NOT NULL,
    meet_link VARCHAR(500),
    calendar_event_id VARCHAR(255),
    calendar_link VARCHAR(1000),
    organizer_email VARCHAR(254) NOT NULL,
    cancel_reason VARCHAR(500),
    cancelled_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_interview_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_interview_application ON interview (application_id, start_at);
CREATE INDEX idx_interview_start ON interview (start_at);
