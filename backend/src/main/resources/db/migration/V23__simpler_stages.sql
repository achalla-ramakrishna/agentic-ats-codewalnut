-- Seven stages instead of thirteen (PIPE-15, ADR-0024): Applied / Sourced, Interviewed,
-- Shortlisted, Offer sent, Joined, On hold, Rejected. Candidates at a retired stage move to the
-- stage that replaced it. History (application_event) keeps the old stage names on purpose.
UPDATE application SET stage = 'SOURCED' WHERE stage = 'SCREENING';
UPDATE application SET stage = 'SHORTLISTED' WHERE stage IN ('SUBMITTED_TO_CLIENT', 'CLIENT_INTERVIEW');
UPDATE application SET stage = 'OFFER_SENT' WHERE stage IN ('SELECTED', 'OFFER_ACCEPTED');
UPDATE application SET stage = 'REJECTED' WHERE stage = 'WITHDRAWN';
