CREATE TABLE chat_message (
                              message_id UUID PRIMARY KEY,
                              room_id UUID NOT NULL,
                              user_id UUID NOT NULL,
                              sender_nickname VARCHAR(100) NOT NULL,
                              content TEXT NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 외래키 제약조건 (방이 삭제되면 해당 방의 메시지도 함께 지워지도록 CASCADE 설정)
                              CONSTRAINT fk_message_chatroom FOREIGN KEY (room_id)
                                  REFERENCES local_chatroom (room_id) ON DELETE CASCADE
);

-- 💡 [성능 최적화] 복합 인덱스 생성
-- 특정 채팅방 진입 시 "과거 메시지 타임라인 조회" 쿼리의 속도를 극대화합니다.
CREATE INDEX idx_message_room_created_at ON chat_message (room_id, created_at DESC);