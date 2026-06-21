package com.butingbe.domain.user.entity;

import com.butingbe.global.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_users_provider_provider_id",
          columnNames = {"provider", "provider_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(unique = true, length = 100)
  private String email;

  @Column(length = 30)
  private String provider;

  @Column(name = "provider_id", length = 100)
  private String providerId;

  @Embedded private Name name;

  @Column(nullable = false, length = 50)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Builder
  public User(
      String email, String provider, String providerId, Name name, String nickname, UserRole role) {
    this.email = email;
    this.provider = provider;
    this.providerId = providerId;
    this.name = name;
    this.nickname = nickname;
    this.role = role != null ? role : UserRole.USER;
  }

  public void linkOAuthProvider(String provider, String providerId) {
    this.provider = provider;
    this.providerId = providerId;
  }
}
