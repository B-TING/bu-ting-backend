package com.butingbe.domain.chat.entity;

import com.butingbe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember {

    @EmbeddedId
    private ChatMemberId id;

    @MapsId("roomId") // ChatMemberId 내부의 roomId 필드와 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
    private LocalChatroom chatroom;

    @MapsId("userId") // ChatMemberId 내부의 userId 필드와 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT))
    private User user;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "last_read_at", nullable = false)
    private OffsetDateTime lastReadAt;

    @Builder
    public ChatMember(LocalChatroom chatroom, User user) {
        this.id = new ChatMemberId(chatroom.getRoomId(), user.getId());
        this.chatroom = chatroom;
        this.user = user;
        this.joinedAt = OffsetDateTime.now();
        this.lastReadAt = OffsetDateTime.now();
    }

    // 유저가 실시간 대화창을 확인하거나 활성화할 때 읽은 시점 갱신 로직
    public void updateLastReadAt() {
        this.lastReadAt = OffsetDateTime.now();
    }
}
