-- Job-portal details for candidates added from a portal export (ADR-0026): experience, current role,
-- key skills, headline, notice period, location, match and summary, as JSON. Staff only.
ALTER TABLE application ADD COLUMN portal_profile TEXT NULL;
