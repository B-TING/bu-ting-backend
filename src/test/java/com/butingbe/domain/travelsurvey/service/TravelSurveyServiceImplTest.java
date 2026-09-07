package com.butingbe.domain.travelsurvey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.travelsurvey.dto.response.TravelSurveyProfileResDto;
import com.butingbe.domain.travelsurvey.entity.TravelSurvey;
import com.butingbe.domain.travelsurvey.repository.TravelSurveyRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelSurveyServiceImplTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private TravelSurveyRepository travelSurveyRepository;
  @Mock private UserRepository userRepository;
  @Mock private OpaqueTokenRepository opaqueTokenRepository;

  @InjectMocks private TravelSurveyServiceImpl travelSurveyService;

  private User user;
  private TravelSurveyProfileReqDto request;

  @BeforeEach
  void setUp() {
    user = user(USER_ID, "user@example.com", "tester", UserRole.USER);
    request =
        new TravelSurveyProfileReqDto(
            "ko", true, false, true, false, true, List.of("food"), List.of(), false);
  }

  @Test
  @DisplayName("설문이 없으면 새로 저장한다")
  void upsertProfileSavesWhenSurveyIsAbsent() {
    AuthenticatedUser authenticatedUser = authenticatedUser(UserRole.USER);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(travelSurveyRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(travelSurveyRepository.save(any(TravelSurvey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TravelSurveyProfileResDto response =
        travelSurveyService.upsertProfile(authenticatedUser, request);

    verify(travelSurveyRepository).save(any(TravelSurvey.class));
    assertThat(response.preferredLanguage()).isEqualTo("ko");
  }

  @Test
  @DisplayName("설문이 이미 있으면 새로 저장하지 않고 기존 설문을 갱신한다")
  void upsertProfileUpdatesExistingSurvey() {
    AuthenticatedUser authenticatedUser = authenticatedUser(UserRole.USER);
    TravelSurvey existing =
        new TravelSurvey(
            user,
            new TravelSurveyProfileReqDto(
                "en", false, true, false, true, false, List.of("shopping"), List.of(), false));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(travelSurveyRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

    TravelSurveyProfileResDto response =
        travelSurveyService.upsertProfile(authenticatedUser, request);

    verify(travelSurveyRepository, never()).save(any(TravelSurvey.class));
    assertThat(response.preferredLanguage()).isEqualTo("ko");
    assertThat(response.isPlanned()).isTrue();
  }

  @Test
  @DisplayName("저장된 설문을 조회한다")
  void getProfileReturnsStoredSurvey() {
    AuthenticatedUser authenticatedUser = authenticatedUser(UserRole.USER);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(travelSurveyRepository.findById(USER_ID))
        .thenReturn(Optional.of(new TravelSurvey(user, request)));

    TravelSurveyProfileResDto response = travelSurveyService.getProfile(authenticatedUser);

    assertThat(response.preferredLanguage()).isEqualTo("ko");
  }

  @Test
  @DisplayName("저장된 설문이 없으면 조회는 실패한다")
  void getProfileThrowsWhenSurveyIsAbsent() {
    AuthenticatedUser authenticatedUser = authenticatedUser(UserRole.USER);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(travelSurveyRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> travelSurveyService.getProfile(authenticatedUser))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("travel survey profile not found.");
  }

  @Test
  @DisplayName("인증 정보가 없으면 UnauthenticatedException을 던진다")
  void rejectsNullAuthenticatedUser() {
    assertThatThrownBy(() -> travelSurveyService.getProfile(null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelSurveyService.upsertProfile(null, request))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("인증 정보에 사용자 id가 없으면 UnauthenticatedException을 던진다")
  void rejectsAuthenticatedUserWithoutId() {
    AuthenticatedUser withoutId =
        new AuthenticatedUser(null, "user@example.com", "tester", List.of());

    assertThatThrownBy(() -> travelSurveyService.getProfile(withoutId))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("인증된 사용자가 DB에 없으면 UnauthenticatedException을 던진다")
  void rejectsUnknownUser() {
    AuthenticatedUser authenticatedUser = authenticatedUser(UserRole.USER);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> travelSurveyService.getProfile(authenticatedUser))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("레거시 개발 관리자 계정은 지우고 새 계정으로 다시 만든다")
  void replacesLegacyDevelopmentAdmin() {
    UUID legacyId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    User legacyAdmin = user(legacyId, "admin@local.dev", "admin", UserRole.ADMIN);
    AuthenticatedUser adminUser =
        new AuthenticatedUser(
            legacyId,
            "admin@local.dev",
            "admin",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    when(userRepository.findByProviderAndProviderId("development", "admin-token"))
        .thenReturn(Optional.of(legacyAdmin));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(travelSurveyRepository.findById(any())).thenReturn(Optional.empty());
    when(travelSurveyRepository.save(any(TravelSurvey.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    travelSurveyService.upsertProfile(adminUser, request);

    verify(opaqueTokenRepository).deleteByUserId(legacyId);
    verify(userRepository).delete(legacyAdmin);
    verify(userRepository).flush();
    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("레거시가 아닌 개발 관리자 계정은 그대로 사용한다")
  void keepsNonLegacyDevelopmentAdmin() {
    User admin = user(USER_ID, "admin@local.dev", "admin", UserRole.ADMIN);
    AuthenticatedUser adminUser =
        new AuthenticatedUser(
            USER_ID, "admin@local.dev", "admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    when(userRepository.findByProviderAndProviderId("development", "admin-token"))
        .thenReturn(Optional.of(admin));
    when(travelSurveyRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(travelSurveyRepository.save(any(TravelSurvey.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    travelSurveyService.upsertProfile(adminUser, request);

    verify(userRepository, never()).delete(any(User.class));
  }

  private AuthenticatedUser authenticatedUser(UserRole role) {
    return new AuthenticatedUser(
        USER_ID,
        "user@example.com",
        "tester",
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  private User user(UUID id, String email, String nickname, UserRole role) {
    User created =
        User.builder()
            .email(email)
            .provider("google")
            .providerId("google-" + nickname)
            .name(new Name("Kim", "Tester"))
            .nickname(nickname)
            .role(role)
            .build();
    ReflectionTestUtils.setField(created, "id", id);
    return created;
  }
}
