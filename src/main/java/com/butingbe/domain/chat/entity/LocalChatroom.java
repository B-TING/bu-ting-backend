package com.butingbe.domain.chat.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "local_chatroom")
public class LocalChatroom {

  @Id
  @GeneratedValue(generator = "UUID")
  private UUID roomId;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "description", length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "chat_zone", nullable = false, length = 50)
  private ChatZone chatZone;

  @Column(name = "max_members", nullable = false)
  private Integer maxMembers;

  @Column(name = "current_members", nullable = false)
  private Integer currentMembers;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public LocalChatroom(
      String title,
      String description,
      ChatZone chatZone,
      Integer maxMembers) { // 💡 localCode 인자 완벽 제거!
    this.title = title;
    this.description = description;
    this.chatZone = chatZone;
    this.maxMembers = maxMembers;
    this.currentMembers = 0;
  }
}
