package com.butingbe.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMemberId implements Serializable {

  @Column(name = "room_id")
  private UUID roomId;

  @Column(name = "user_id")
  private UUID userId;

  public ChatMemberId(UUID roomId, UUID userId) {
    this.roomId = roomId;
    this.userId = userId;
  }
}
