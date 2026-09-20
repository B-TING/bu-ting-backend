package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocalChatroomRepository extends JpaRepository<LocalChatroom, UUID> {

  List<LocalChatroom> findByChatZone(ChatZone chatZone);

  /**
   * 정원 확인과 인원 증가를 한 문장으로 처리한다. 읽고 비교한 뒤 증가시키면 동시 입장에서 정원을 넘길 수 있다.
   *
   * @return 갱신된 행 수. 0이면 정원이 가득 찼거나 방이 없다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update LocalChatroom c
      set c.currentMembers = c.currentMembers + 1
      where c.id = :roomId and c.currentMembers < c.maxMembers
      """)
  int increaseCurrentMembers(@Param("roomId") UUID roomId);

  /**
   * 인원을 하나 줄인다. 0 아래로는 내려가지 않는다.
   *
   * @return 갱신된 행 수. 0이면 이미 0명이었거나 방이 없다.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update LocalChatroom c
      set c.currentMembers = c.currentMembers - 1
      where c.id = :roomId and c.currentMembers > 0
      """)
  int decreaseCurrentMembers(@Param("roomId") UUID roomId);

  /** 벌크 갱신 직후의 인원수. 영속성 컨텍스트에 남은 엔티티는 갱신 전 값이라 그대로 읽으면 안 된다. */
  @Query("select c.currentMembers from LocalChatroom c where c.id = :roomId")
  Optional<Integer> findCurrentMembers(@Param("roomId") UUID roomId);
}
