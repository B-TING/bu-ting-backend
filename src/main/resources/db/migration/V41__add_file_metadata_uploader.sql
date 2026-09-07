-- Track who uploaded each file so on-site submissions can verify ownership (#221).
-- Nullable: pre-existing rows have no recorded uploader.

ALTER TABLE file_metadata ADD COLUMN uploader_id UUID;
