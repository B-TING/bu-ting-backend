-- 지역/랜드마크별 오픈채팅방 테이블 생성
CREATE TABLE local_chatroom (
                                room_id UUID PRIMARY KEY,
                                title VARCHAR(100) NOT NULL,
                                description VARCHAR(500),
                                local_code VARCHAR(20) NOT NULL,
                                landmark_name VARCHAR(100),
                                google_place_id VARCHAR(100) NOT NULL,
                                latitude NUMERIC(10, 7) NOT NULL,
                                longitude NUMERIC(10, 7) NOT NULL,
                                max_members INTEGER NOT NULL DEFAULT 100,
                                current_members INTEGER NOT NULL DEFAULT 0,
                                creator_id UUID NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT chk_max_members CHECK (max_members > 0),
                                CONSTRAINT chk_current_members CHECK (current_members >= 0 AND current_members <= max_members),
                                CONSTRAINT chk_latitude CHECK (latitude >= -90 AND latitude <= 90),
                                CONSTRAINT chk_longitude CHECK (longitude >= -180 AND longitude <= 180)
);

-- 채팅방 참여 멤버 매핑 테이블 생성
CREATE TABLE chat_member (
                             room_id UUID NOT NULL,
                             user_id UUID NOT NULL,
                             joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             last_read_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             PRIMARY KEY (room_id, user_id),
                             CONSTRAINT fk_chat_member_room FOREIGN KEY (room_id)
                                 REFERENCES local_chatroom (room_id) ON DELETE CASCADE
);

CREATE INDEX idx_chatroom_coordinates ON local_chatroom (latitude, longitude);

CREATE INDEX idx_chatroom_local_code ON local_chatroom (local_code);

CREATE INDEX idx_chat_member_user ON chat_member (user_id, joined_at DESC);


