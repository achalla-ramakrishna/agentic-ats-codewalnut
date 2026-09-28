-- Simple hiring tracker: clients, openings, candidates, applications and their history.
CREATE TABLE client (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    notes TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_client_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_opening (
    id BINARY(16) NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    client_id BINARY(16),
    hiring_type VARCHAR(30) NOT NULL,
    openings INT,
    status VARCHAR(20) NOT NULL,
    description TEXT,
    created_by VARCHAR(254),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_job_opening_client FOREIGN KEY (client_id) REFERENCES client (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidate (
    id BINARY(16) NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(30),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_candidate_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_candidate_phone ON candidate (phone);

CREATE TABLE application (
    id BINARY(16) NOT NULL PRIMARY KEY,
    job_id BINARY(16) NOT NULL,
    candidate_id BINARY(16) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_application_job_candidate UNIQUE (job_id, candidate_id),
    CONSTRAINT fk_application_job FOREIGN KEY (job_id) REFERENCES job_opening (id),
    CONSTRAINT fk_application_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Append-only history: created, stage changes and notes.
CREATE TABLE application_event (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    type VARCHAR(20) NOT NULL,
    from_stage VARCHAR(30),
    to_stage VARCHAR(30),
    note TEXT,
    actor_email VARCHAR(254),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_application_event_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_application_event_created ON application_event (created_at);
CREATE INDEX idx_application_event_application ON application_event (application_id, created_at);
