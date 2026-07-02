package com.butingbe.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.ChatMemberId;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.support.AbstractContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest
@Transactional
class ChatMemberRepositoryTest extends AbstractContainerTest {

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private EntityManager em;

    private UUID roomId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // 1. 부모 엔티티(User) 저장
        User testUser = User.builder()
                .nickname("수영구보안관")
                .name(new Name("조", "준연"))
                .email("test@test.com")
                .build();
        em.persist(testUser);
        userId = testUser.getId();

        // 2. 부모 엔티티(LocalChatroom) 저장
        // 💡 NOT NULL 제약조건이 걸린 max_members와 필수 값들을 채워줍니다.
        LocalChatroom testRoom = LocalChatroom.builder()
                .title("수영구 오픈채팅방")
                .maxMembers(30)              // 💡 이 부분이 누락되어 에러가 났던 것입니다!
                .chatZone(ChatZone.SUYEONG_NAMGU) // 💡 대화방에 권역(ChatZone) 정보가 필수라면 함께 세팅
                .build();
        em.persist(testRoom);
        roomId = testRoom.getRoomId();

        // 3. ChatMember 생성 및 영속화
        ChatMember chatMember = ChatMember.builder()
                .chatroom(testRoom)
                .user(testUser)
                .build();
        em.persist(chatMember);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("채팅방 멤버 존재 여부 확인 (UUID)")
    void existsByIdRoomIdAndIdUserId() {
        boolean exists = chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("채팅방 멤버 복합키 기반 삭제 (UUID)")
    void deleteByIdRoomIdAndIdUserId() {
        chatMemberRepository.deleteByIdRoomIdAndIdUserId(roomId, userId);
        em.flush();
        em.clear();

        boolean exists = chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId);
        assertThat(exists).isFalse();
    }
}