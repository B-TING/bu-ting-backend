package com.butingbe.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "local_chatroom",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_chatroom_place_id", columnNames = "google_place_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalChatroom {

    @Id
    @Column(name = "room_id", columnDefinition = "UUID")
    private UUID roomId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "local_code", nullable = false, length = 20)
    private String localCode;

    @Column(name = "google_place_id", nullable = false, length = 100)
    private String googlePlaceId;

    @Column(name = "landmark_name", nullable = false, length = 100)
    private String landmarkName;

    // Google Map 연동을 위한 NUMERIC(10,7) 매핑
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "max_members", nullable = false)
    private Integer maxMembers;

    @Column(name = "current_members", nullable = false)
    private Integer currentMembers;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public LocalChatroom(String title, String description, String localCode,
                         String googlePlaceId, String landmarkName,
                         BigDecimal latitude, BigDecimal longitude,
                         Integer maxMembers, UUID creatorId) {
        this.roomId = UUID.randomUUID(); // Java 애플리케이션 단에서 UUID 선할당
        this.title = title;
        this.description = description;
        this.localCode = localCode;
        this.googlePlaceId = googlePlaceId;
        this.landmarkName = landmarkName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxMembers = (maxMembers != null) ? maxMembers : 100;
        this.currentMembers = 0;
        this.creatorId = creatorId;
    }

    // 비즈니스 로직: 유저 입장 시 인원수 증가 (정원 체크는 서비스 레이어에서 수행)
    public void incrementCurrentMembers() {
        if (this.currentMembers >= this.maxMembers) {
            throw new IllegalStateException("채팅방 정원이 가득 찼습니다.");
        }
        this.currentMembers++;
    }

    // 비즈니스 로직: 유저 완전히 나가기 시 인원수 차감
    public void decrementCurrentMembers() {
        if (this.currentMembers > 0) {
            this.currentMembers--;
        }
    }
}
