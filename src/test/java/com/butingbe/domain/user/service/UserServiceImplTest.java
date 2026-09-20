package com.butingbe.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.UpdateMyProfileReqDto;
import com.butingbe.domain.user.dto.response.MyProfileResDto;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // 💡 Spring 대신 Mockito 환경만 빠르게 실행!
@DisplayName("UserServiceImpl 단위 테스트")
class UserServiceImplTest {

  @InjectMocks private UserServiceImpl userService; // Mock 객체들이 주입될 대상

  @Mock private UserRepository userRepository; // 가짜 객체(Mock)로 대체
  @Mock private OpaqueTokenRepository opaqueTokenRepository;

  private AuthenticatedUser authenticatedUser(UUID userId) {
    return new AuthenticatedUser(userId, "me@example.com", "테스터", java.util.List.of());
  }

  @Test
  @DisplayName("인증 정보가 없거나 id가 없으면 UnauthenticatedException을 던진다")
  void rejectsUnauthenticatedUser() {
    AuthenticatedUser withoutId =
        new AuthenticatedUser(null, "me@example.com", "테스터", java.util.List.of());

    assertThatThrownBy(() -> userService.getMyProfile(null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> userService.getMyProfile(withoutId))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> userService.deleteMyAccount(null))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Nested
  @DisplayName("내 프로필 관리 테스트")
  class MyProfileTest {

    @Test
    @DisplayName("인증된 사용자 ID로 내 프로필을 조회한다")
    void getMyProfileSuccess() {
      // given
      UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
      User user =
          User.builder()
              .id(userId)
              .email("me@example.com")
              .nickname("기존닉")
              .provider("google")
              .providerId("google-123")
              .name(new Name("홍", "길동"))
              .build();
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      MyProfileResDto response = userService.getMyProfile(authenticatedUser(userId));

      // then
      assertThat(response.email()).isEqualTo("me@example.com");
      assertThat(response.nickname()).isEqualTo("기존닉");
      assertThat(response.provider()).isEqualTo("google");
      assertThat(response.firstName()).isEqualTo("길동");
      assertThat(response.lastName()).isEqualTo("홍");
    }

    @Test
    @DisplayName("내 프로필 수정 요청이면 닉네임과 프로필 정보를 갱신한다")
    void updateMyProfileSuccess() {
      // given
      UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
      User user =
          User.builder()
              .id(userId)
              .email("me@example.com")
              .nickname("기존닉")
              .provider("google")
              .providerId("google-123")
              .name(new Name("홍", "길동"))
              .build();
      UpdateMyProfileReqDto request =
          new UpdateMyProfileReqDto("수정닉", "https://example.com/profile.png", "영희", "김");
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      MyProfileResDto response = userService.updateMyProfile(authenticatedUser(userId), request);

      // then
      assertThat(response.nickname()).isEqualTo("수정닉");
      assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
      assertThat(response.firstName()).isEqualTo("영희");
      assertThat(response.lastName()).isEqualTo("김");
    }

    @Test
    @DisplayName("회원 탈퇴 시 사용자의 토큰을 먼저 삭제하고 사용자를 삭제한다")
    void deleteMyAccountSuccess() {
      // given
      UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
      User user = User.builder().id(userId).email("me@example.com").nickname("탈퇴유저").build();
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      // when
      userService.deleteMyAccount(authenticatedUser(userId));

      // then
      verify(opaqueTokenRepository).deleteByUserId(userId);
      verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("인증된 사용자 ID가 DB에 없으면 UnauthenticatedException이 발생한다")
    void getMyProfileFailWithUnknownUser() {
      // given
      UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userService.getMyProfile(authenticatedUser(userId)))
          .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    @DisplayName("개발 관리자 정보가 DB에 없으면 자동 생성 후 프로필을 반환한다")
    void getMyProfileCreatesDevelopmentAdmin() {
      // given
      AuthenticatedUser admin = AuthenticatedUser.developmentAdmin();
      UUID createdAdminId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
      User createdAdmin =
          User.builder()
              .id(createdAdminId)
              .email(admin.email())
              .nickname(admin.nickname())
              .provider("development")
              .providerId("admin-token")
              .name(new Name("개발", "관리자"))
              .role(com.butingbe.domain.user.entity.UserRole.ADMIN)
              .build();
      given(userRepository.findByProviderAndProviderId("development", "admin-token"))
          .willReturn(Optional.empty());
      given(userRepository.findByEmail(admin.email())).willReturn(Optional.empty());
      given(userRepository.save(any(User.class))).willReturn(createdAdmin);

      // when
      MyProfileResDto response = userService.getMyProfile(admin);

      // then
      assertThat(response.userId()).isEqualTo(createdAdminId.toString());
      assertThat(response.email()).isEqualTo("admin@local.dev");
      assertThat(response.nickname()).isEqualTo("개발 관리자");
      assertThat(response.provider()).isEqualTo("development");
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(userCaptor.capture());
      User savedUser = userCaptor.getValue();
      assertThat(savedUser.getId()).isNull();
      assertThat(savedUser.getEmail()).isEqualTo("admin@local.dev");
      assertThat(savedUser.getProvider()).isEqualTo("development");
      assertThat(savedUser.getProviderId()).isEqualTo("admin-token");
      assertThat(savedUser.getRole()).isEqualTo(com.butingbe.domain.user.entity.UserRole.ADMIN);
      assertThat(savedUser.getName().getFirstName()).isEqualTo("관리자");
      assertThat(savedUser.getName().getLastName()).isEqualTo("개발");
    }

    @Test
    @DisplayName("레거시 개발 관리자 더미 ID가 있으면 DB 발급 UUID 사용자로 교체한다")
    void getMyProfileReplacesLegacyDevelopmentAdmin() {
      // given
      AuthenticatedUser admin = AuthenticatedUser.developmentAdmin();
      UUID legacyAdminId = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID createdAdminId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
      User legacyAdmin =
          User.builder()
              .id(legacyAdminId)
              .email(admin.email())
              .nickname(admin.nickname())
              .provider("development")
              .providerId("admin-token")
              .name(new Name("개발", "관리자"))
              .role(com.butingbe.domain.user.entity.UserRole.ADMIN)
              .build();
      User createdAdmin =
          User.builder()
              .id(createdAdminId)
              .email(admin.email())
              .nickname(admin.nickname())
              .provider("development")
              .providerId("admin-token")
              .name(new Name("개발", "관리자"))
              .role(com.butingbe.domain.user.entity.UserRole.ADMIN)
              .build();
      given(userRepository.findByProviderAndProviderId("development", "admin-token"))
          .willReturn(Optional.of(legacyAdmin));
      given(userRepository.save(any(User.class))).willReturn(createdAdmin);

      // when
      MyProfileResDto response = userService.getMyProfile(admin);

      // then
      assertThat(response.userId()).isEqualTo(createdAdminId.toString());
      verify(opaqueTokenRepository).deleteByUserId(legacyAdminId);
      verify(userRepository).delete(legacyAdmin);
      verify(userRepository).flush();
      verify(userRepository).save(any(User.class));
    }
  }
}
