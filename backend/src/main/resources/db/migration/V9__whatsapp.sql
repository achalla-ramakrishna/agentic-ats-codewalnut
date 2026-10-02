-- WhatsApp on candidate messages. whatsapp_status: OPENED (sent by the recruiter from WhatsApp
-- with the text pre-filled), SENT / DELIVERED / READ / FAILED (WhatsApp Business API), RECEIVED
-- (a candidate's reply that came in through the API webhook).
ALTER TABLE message
    ADD COLUMN whatsapp_status VARCHAR(20),
    ADD COLUMN whatsapp_message_id VARCHAR(255);

CREATE UNIQUE INDEX uk_message_whatsapp_id ON message (whatsapp_message_id);
