-- Phase 2: show a human-readable sender name (also carried by the federation envelope later).
ALTER TABLE mail_messages ADD COLUMN sender_display_name VARCHAR(128);

-- Supports "messages authored by user X" lookups (sent folder ownership, recall).
CREATE INDEX idx_mail_messages_sender_user ON mail_messages (sender_user_id);
