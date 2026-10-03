-- CodeWalnut-branded résumés (ADR-0012): the editable content per candidate per opening. The
-- generated PDF is stored as a CODEWALNUT_RESUME document when someone saves it.
CREATE TABLE codewalnut_resume (
    id BINARY(16) NOT NULL PRIMARY KEY,
    application_id BINARY(16) NOT NULL,
    data MEDIUMTEXT NOT NULL,
    show_email BIT(1) NOT NULL DEFAULT b'1',
    include_screening BIT(1) NOT NULL DEFAULT b'1',
    source_document_id BINARY(16),
    saved_document_id BINARY(16),
    model VARCHAR(100),
    updated_by VARCHAR(254) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_codewalnut_resume_application UNIQUE (application_id),
    CONSTRAINT fk_codewalnut_resume_application FOREIGN KEY (application_id) REFERENCES application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
