ALTER TABLE reai_connection
    ADD COLUMN client_id VARCHAR(160),
    ADD COLUMN client_secret TEXT,
    ADD COLUMN granted_scope VARCHAR(512);
