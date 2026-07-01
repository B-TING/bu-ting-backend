ALTER TABLE local_chatroom DROP COLUMN IF EXISTS latitude;
ALTER TABLE local_chatroom DROP COLUMN IF EXISTS longitude;
ALTER TABLE local_chatroom DROP COLUMN IF EXISTS google_place_id;
ALTER TABLE local_chatroom DROP COLUMN IF EXISTS landmark_name;

ALTER TABLE local_chatroom ADD COLUMN chat_zone VARCHAR(50) NOT NULL;

DROP INDEX IF EXISTS idx_chatroom_coordinates;

CREATE INDEX idx_chatroom_chat_zone ON local_chatroom (chat_zone);