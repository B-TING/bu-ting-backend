package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest // 전체 컨텍스트를 로드하여 빈을 주입받음
@Transactional  // 테스트 완료 후 DB에 insert된 데이터를 자동 롤백시킴
class LocalChatroomRepositoryTest {

    @Autowired
    private LocalChatroomRepository localChatroomRepository;

    @Test
    @DisplayName("특정 ChatZone을 인자로 던지면 해당 권역에 속한 오픈채팅방만 정확히 필터링하여 조회한다")
    void findByChatZone_success() {
        // Given
        UUID creatorId = UUID.randomUUID();
        LocalChatroom suyeongRoom = LocalChatroom.builder()
                .title("수영구 남구")
                .localCode("26500")
                .description("수영 남구 야호!!!!!")
                .chatZone(ChatZone.SUYEONG_NAMGU)
                .maxMembers(30)
                .build();
        localChatroomRepository.save(suyeongRoom);

        // When
        List<LocalChatroom> result = localChatroomRepository.findByChatZone(ChatZone.SUYEONG_NAMGU);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("광안리 모여라");
    }
}