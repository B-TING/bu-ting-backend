package com.butingbe.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.notification.dto.response.NotificationSettingsResDto;
import com.butingbe.domain.notification.dto.response.PushLogResDto;
import com.butingbe.domain.notification.entity.DevicePlatform;
import com.butingbe.domain.notification.repository.PushNotificationLogRepository;
import com.butingbe.domain.notification.repository.UserDeviceTokenRepository;
import com.butingbe.domain.notification.repository.UserZoneSubscriptionRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationServiceTest extends AbstractContainerTest {

  @Autowired private NotificationService notificationService;
  @Autowired private UserDeviceTokenRepository deviceTokenRepository;
  @Autowired private UserZoneSubscriptionRepository subscriptionRepository;
  @Autowired private PushNotificationLogRepository pushLogRepository;
  @Autowired private UserRepository userRepository;
  @MockitoBean private PushSender pushSender;

  private AuthenticatedUser user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    when(pushSender.send(any(), anyString(), anyString()))
        .thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
    userId = savedUser("u").getId();
    user = new AuthenticatedUser(userId, "u@example.com", "u", List.of());
  }

  @Test
  @DisplayName("토큰은 upsert되고 삭제된다")
  void tokenUpsertAndDelete() {
    notificationService.upsertToken(user, "tok-1", "ios");
    notificationService.upsertToken(user, "tok-1", "android"); // 같은 토큰 → 갱신
    assertThat(deviceTokenRepository.findByFcmToken("tok-1").orElseThrow().getPlatform())
        .isEqualTo(DevicePlatform.ANDROID);

    notificationService.deleteToken(user, "tok-1");
    assertThat(deviceTokenRepository.findByFcmToken("tok-1")).isEmpty();
  }

  @Test
  @DisplayName("관심 구역은 전체 교체된다")
  void subscriptionsReplaced() {
    notificationService.setSubscriptions(
        user, List.of("SUYEONG_NAMGU", "YEONGDO", "SUYEONG_NAMGU"));
    assertThat(notificationService.getSubscriptions(user).zoneIds())
        .containsExactlyInAnyOrder("SUYEONG_NAMGU", "YEONGDO"); // 중복 제거

    notificationService.setSubscriptions(user, List.of("CENTRAL_NORTH"));
    assertThat(notificationService.getSubscriptions(user).zoneIds())
        .containsExactly("CENTRAL_NORTH");
  }

  @Test
  @DisplayName("알림 설정은 기본 켜짐이고 부분 갱신된다")
  void settingsDefaultOnAndPatch() {
    NotificationSettingsResDto before = notificationService.getSettings(user);
    assertThat(before.settings().values()).allMatch(Boolean::booleanValue);

    NotificationSettingsResDto after =
        notificationService.updateSettings(user, Map.of("LIKE_DEADLINE", false));
    assertThat(after.settings().get("LIKE_DEADLINE")).isFalse();
    assertThat(after.settings().get("ROUND_OPEN")).isTrue();
    // 재갱신(다시 켜기)
    assertThat(
            notificationService
                .updateSettings(user, Map.of("LIKE_DEADLINE", true))
                .settings()
                .get("LIKE_DEADLINE"))
        .isTrue();
  }

  @Test
  @DisplayName("운영자 구역 푸시는 구독자 토큰으로 보내고 이력을 남긴다")
  void operatorPushToZone() {
    AuthenticatedUser subscriber =
        new AuthenticatedUser(savedUser("s").getId(), "s@e.com", "s", List.of());
    notificationService.setSubscriptions(subscriber, List.of("SUYEONG_NAMGU"));
    notificationService.upsertToken(subscriber, "sub-tok", "IOS");
    AuthenticatedUser operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@e.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    PushLogResDto result =
        notificationService.operatorPush(
            operator, "ZONE_TOPIC", "SUYEONG_NAMGU", "불 들어왔어요", "지금 인증하세요");

    assertThat(result.recipientCount()).isEqualTo(1);
    assertThat(pushLogRepository.findAll()).hasSize(1);
    assertThat(pushLogRepository.findAll().get(0).getTarget()).isEqualTo("zone.SUYEONG_NAMGU");
  }

  @Test
  @DisplayName("운영자 전체 푸시는 모든 토큰으로 보낸다")
  void operatorPushToAll() {
    notificationService.upsertToken(user, "t1", "IOS");
    AuthenticatedUser operator =
        new AuthenticatedUser(
            savedUser("op2").getId(),
            "op2@e.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));

    PushLogResDto result = notificationService.operatorPush(operator, "ALL", null, "공지", "전체 공지");
    assertThat(result.recipientCount()).isGreaterThanOrEqualTo(1);
    assertThat(pushLogRepository.findAll().get(0).getTarget()).isEqualTo("ALL");
  }

  @Test
  @DisplayName("일반 유저의 운영 푸시는 403이다")
  void operatorPushForbidden() {
    assertThatThrownBy(() -> notificationService.operatorPush(user, "ALL", null, "t", "b"))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("잘못된 플랫폼·구역·타입은 400이다")
  void invalidInputs() {
    assertThatThrownBy(() -> notificationService.upsertToken(user, "t", "WINDOWS"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> notificationService.setSubscriptions(user, List.of("NOWHERE")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> notificationService.updateSettings(user, Map.of("GHOST", false)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("구독자 없는 구역 푸시는 0명이고, 미인증 조회는 401이다")
  void emptyZoneAndUnauthenticated() {
    AuthenticatedUser operator =
        new AuthenticatedUser(
            savedUser("op3").getId(),
            "op3@e.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    assertThat(
            notificationService
                .operatorPush(operator, "ZONE_TOPIC", "YEONGDO", "t", "b")
                .recipientCount())
        .isZero();

    assertThatThrownBy(() -> notificationService.getSettings(null))
        .isInstanceOf(com.butingbe.global.error.exception.UnauthenticatedException.class);
  }

  private User savedUser(String nick) {
    return userRepository.save(
        User.builder()
            .email(nick + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nick)
            .role(UserRole.USER)
            .build());
  }
}
