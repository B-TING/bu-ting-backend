package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMessage;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ChatMessageRepositoryTest extends AbstractContainerTest {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private EntityManager em;

    private UUID roomId;
    private UUID userId;
    private String nickname;

    @BeforeEach
    void setUp() {
        // 1. 제약조건을 충족하는 User 생성 및 저장
        User testUser = User.builder()
                .nickname("수영구보안관")
                .name(new Name("조", "준연"))
                .email("test@test.com")
                .build();
        em.persist(testUser);
        userId = testUser.getId();
        nickname = testUser.getNickname();

        // 2. 제약조건을 충족하는 LocalChatroom 생성 및 저장
        LocalChatroom testRoom = LocalChatroom.builder()
                .title("수영구 오픈채팅방")
                .maxMembers(30)
                // 만약 ChatZone이 필수라면 .chatZone(ChatZone.SUYEONG_NAMGU) 추가
                .build();
        em.persist(testRoom);
        roomId = testRoom.getRoomId();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("특정 채팅방의 메시지를 최신순으로 최대 100개까지 정확히 조회한다")
    void findTop100ByRoomIdOrderByCreatedAtDesc() {
        // Given
        // 테스트를 위해 메시지 5개를 시간 간격을 두고 저장 (JPA가 알아서 가상 테이블에 시간 역순 배치)
        for (int i = 1; i <= 5; i++) {
            ChatMessage message = ChatMessage.builder()
                    .roomId(roomId)
                    .userId(userId)
                    .senderNickname(nickname)
                    .content("테팅 메시지 " + i)
                    .build();
            chatMessageRepository.save(message);

            // 💡 @CreationTimestamp 또는 @CreatedDate가 밀리초 단위로 다르게 찍히도록
            // 아주 미세한 슬립을 주거나 하이버네이트 자동 생성에 맡깁니다.
            try { Thread.sleep(10); } catch (InterruptedException e) { e.printStackTrace(); }
        }

        // 다른 방 메시지 1개 생성 (조회 결과에 섞이지 않는지 검증용)
        ChatMessage otherRoomMessage = ChatMessage.builder()
                .roomId(UUID.randomUUID()) // 다른 방 ID
                .userId(userId)
                .senderNickname(nickname)
                .content("다른 방 메시지")
                .build();
        chatMessageRepository.save(otherRoomMessage);

        em.flush();
        em.clear();

        // When
        List<ChatMessage> chatMessages = chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId);

        // Then
        // 1. 전체 데이터 개수 검증 (다른 방 메시지는 제외되고 해당 방 것 5개만 나와야 함)
        assertThat(chatMessages).hasSize(5);

        // 2. 최신순(내림차순) 정렬 검증 (가장 마지막에 넣은 '테팅 메시지 5'가 인덱스 0번에 와야 함)
        assertThat(chatMessages.get(0).getContent()).isEqualTo("테팅 메시지 5");
        assertThat(chatMessages.get(4).getContent()).isEqualTo("테팅 메시지 1");
    }
}