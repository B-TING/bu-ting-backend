package com.butingbe.domain.user.entity;

import com.butingbe.global.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  // 🔑 OAuth 2.0 대비용 필드 추가
  //    @Column(nullable = false, length = 20)
  //    private String provider;     // kakao, google, naver 등
  //
  //    @Column(nullable = false, unique = true, length = 100)
  //    private String providerId;   // 소셜사에서 넘겨주는 유저 고유 식별 ID값

  @Embedded private Name name;

  @Column(nullable = false, length = 50)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Builder
  public User(String email, Name name, String nickname, UserRole role) {
    this.email = email;
    //        this.provider = provider != null ? provider : "LOCAL_MOCK"; // 현재 임시용 default
    //        this.providerId = providerId != null ? providerId : email; // 현재는 임시로 email을 꽂아둠
    this.name = name;
    this.nickname = nickname;
    this.role = role != null ? role : UserRole.USER;
  }
}
