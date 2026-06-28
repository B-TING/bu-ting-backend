package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalChatroomRepository extends JpaRepository<LocalChatroom, UUID> {

    List<LocalChatroom> findByChatZone(ChatZone chatZone);
}
