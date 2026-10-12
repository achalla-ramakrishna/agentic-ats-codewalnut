-- PIPE-25: résumé requests sent before templates were recorded (PIPE-23) get their label, so the
-- outreach sequence knows these candidates were already asked and never asks twice.
UPDATE message
SET template_key = 'resume_request'
WHERE author_type = 'STAFF'
  AND emailed = 1
  AND template_key IS NULL
  AND subject LIKE '%please share your r%sum%';
