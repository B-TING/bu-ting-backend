package com.butingbe.domain.auth.entity;

import com.butingbe.domain.user.entity.User;
import com.butingbe.global.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "opaque_tokens",
    indexes = {
      @Index(name = "idx_opaque_tokens_token_hash", columnList = "token_hash", unique = true),
      @Index(name = "idx_opaque_tokens_user_id", columnList = "user_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpaqueToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Builder
  public OpaqueToken(String tokenHash, User user, LocalDateTime expiresAt) {
    this.tokenHash = tokenHash;
    this.user = user;
    this.expiresAt = expiresAt;
  }

  public boolean isActive(LocalDateTime now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(LocalDateTime now) {
    this.revokedAt = now;
  }
}
