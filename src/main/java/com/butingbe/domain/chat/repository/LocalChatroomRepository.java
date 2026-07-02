package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalChatroomRepository extends JpaRepository<LocalChatroom, UUID> {

  List<LocalChatroom> findByChatZone(ChatZone chatZone);
}
