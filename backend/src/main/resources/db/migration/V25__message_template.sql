-- Which email template a staff message was written from (PIPE-23), so the Workflow view can show
-- who received the résumé request, interview slots, shortlisted email and so on.
ALTER TABLE message ADD COLUMN template_key VARCHAR(40) NULL;
