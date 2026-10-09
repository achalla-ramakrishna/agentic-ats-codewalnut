-- Keep legacy bytes until a separately gated, verified cleanup. No files are deleted here.
ALTER TABLE candidate_document MODIFY COLUMN data LONGBLOB NULL;

-- Durable storage outbox and immutable object manifest. No provider credentials or personal data.
CREATE TABLE background_task (
    id BINARY(16) PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BINARY(16) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    run_after TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token BINARY(16) NULL,
    lease_until TIMESTAMP(6) NULL,
    verified_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_storage_target (target_type, target_id),
    UNIQUE KEY uq_storage_key (storage_key),
    KEY ix_storage_due (status, run_after, lease_until)
);
