-- Shareable job links: public job details, and candidates applying through them.
ALTER TABLE job_opening
    ADD COLUMN location VARCHAR(200),
    ADD COLUMN work_mode VARCHAR(20),
    ADD COLUMN employment_type VARCHAR(100),
    ADD COLUMN public_slug VARCHAR(40),
    ADD COLUMN published BIT(1) NOT NULL DEFAULT b'0';

CREATE UNIQUE INDEX uk_job_opening_public_slug ON job_opening (public_slug);

-- Where the application came from (IMPORT, MANUAL, JOB_LINK) and, for job-link
-- applications, when the candidate agreed to CodeWalnut keeping their data.
ALTER TABLE application
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN consent_at DATETIME(6);
