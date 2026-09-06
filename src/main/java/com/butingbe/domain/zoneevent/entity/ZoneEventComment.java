package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공개 참여에 대한 댓글. 소프트 삭제(deleted_at)를 지원한다. */
@Entity
@Table(name = "zone_event_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventComment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "comment_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "participation_id", nullable = false)
  private UUID participationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 200)
  private String content;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Builder
  private ZoneEventComment(UUID participationId, UUID userId, String content) {
    this.participationId = participationId;
    this.userId = userId;
    this.content = content;
    this.createdAt = OffsetDateTime.now();
    this.updatedAt = this.createdAt;
  }

  /** 댓글 내용을 수정한다. */
  public void edit(String content) {
    this.content = content;
    this.updatedAt = OffsetDateTime.now();
  }

  /** 소프트 삭제. */
  public void softDelete() {
    this.deletedAt = OffsetDateTime.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
