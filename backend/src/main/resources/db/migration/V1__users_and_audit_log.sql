CREATE TABLE app_user (
    id BINARY(16) NOT NULL PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    name VARCHAR(200),
    google_subject VARCHAR(255),
    active BIT(1) NOT NULL DEFAULT b'1',
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_app_user_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user_role (
    user_id BINARY(16) NOT NULL,
    role VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
    id BINARY(16) NOT NULL PRIMARY KEY,
    actor_id BINARY(16),
    actor_email VARCHAR(254),
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(60),
    entity_id VARCHAR(64),
    details TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
