-- Self-booking (PIPE-24): 30-minute (or other) slots a recruiter offers for an opening, the candidate
-- who booked each one, and the interview created when the recruiter sends the invites.
CREATE TABLE interview_slot (
    id BINARY(16) NOT NULL PRIMARY KEY,
    job_id BINARY(16) NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    end_at TIMESTAMP(6) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    interviewer_emails VARCHAR(2000) NULL,
    created_by VARCHAR(254) NOT NULL,
    application_id BINARY(16) NULL,
    booked_at TIMESTAMP(6) NULL,
    interview_id BINARY(16) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_slot_application UNIQUE (application_id),
    CONSTRAINT uq_slot_time UNIQUE (job_id, start_at),
    CONSTRAINT fk_slot_job FOREIGN KEY (job_id) REFERENCES job_opening (id) ON DELETE CASCADE
);

-- A personal "pick your slot" link per candidate application; no sign-in needed.
CREATE TABLE booking_link (
    token VARCHAR(64) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_booking_application UNIQUE (application_id)
);
