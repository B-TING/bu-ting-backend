package com.butingbe.domain.notification.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.notification.dto.response.NotificationSettingsResDto;
import com.butingbe.domain.notification.dto.response.PushLogResDto;
import com.butingbe.domain.notification.dto.response.ZoneSubscriptionsResDto;
import com.butingbe.domain.notification.entity.DevicePlatform;
import com.butingbe.domain.notification.entity.NotificationType;
import com.butingbe.domain.notification.entity.PushNotificationLog;
import com.butingbe.domain.notification.entity.UserDeviceToken;
import com.butingbe.domain.notification.entity.UserNotificationPreference;
import com.butingbe.domain.notification.entity.UserZoneSubscription;
import com.butingbe.domain.notification.repository.PushNotificationLogRepository;
import com.butingbe.domain.notification.repository.UserDeviceTokenRepository;
import com.butingbe.domain.notification.repository.UserNotificationPreferenceRepository;
import com.butingbe.domain.notification.repository.UserZoneSubscriptionRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 디바이스 토큰·관심 구역·알림 설정 관리와 운영 즉시 푸시. */
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final UserDeviceTokenRepository deviceTokenRepository;
  private final UserZoneSubscriptionRepository subscriptionRepository;
  private final UserNotificationPreferenceRepository preferenceRepository;
  private final PushNotificationLogRepository pushLogRepository;
  private final PushSender pushSender;
  private final OperatorAuthorization operatorAuthorization;

  /** 토큰을 등록하거나 갱신한다(같은 토큰이면 소유자·플랫폼만 갱신). */
  @Transactional
  public void upsertToken(AuthenticatedUser user, String fcmToken, String platform) {
    UUID userId = requireUserId(user);
    DevicePlatform parsed = parsePlatform(platform);
    deviceTokenRepository
        .findByFcmToken(fcmToken)
        .ifPresentOrElse(
            token -> token.touch(userId, parsed),
            () ->
                deviceTokenRepository.save(
                    UserDeviceToken.builder()
                        .userId(userId)
                        .fcmToken(fcmToken)
                        .platform(parsed)
                        .build()));
  }

  @Transactional
  public void deleteToken(AuthenticatedUser user, String fcmToken) {
    deviceTokenRepository.deleteByFcmTokenAndUserId(fcmToken, requireUserId(user));
  }

  @Transactional(readOnly = true)
  public ZoneSubscriptionsResDto getSubscriptions(AuthenticatedUser user) {
    UUID userId = requireUserId(user);
    return new ZoneSubscriptionsResDto(
        subscriptionRepository.findByUserId(userId).stream()
            .map(UserZoneSubscription::getZoneId)
            .toList());
  }

  /** 관심 구역을 전체 교체한다. */
  @Transactional
  public ZoneSubscriptionsResDto setSubscriptions(AuthenticatedUser user, List<String> zoneIds) {
    UUID userId = requireUserId(user);
    List<String> validated =
        (zoneIds == null ? List.<String>of() : zoneIds)
            .stream().map(this::parseZone).distinct().toList();
    subscriptionRepository.deleteByUserId(userId);
    for (String zoneId : validated) {
      subscriptionRepository.save(
          UserZoneSubscription.builder().userId(userId).zoneId(zoneId).build());
    }
    return new ZoneSubscriptionsResDto(validated);
  }

  @Transactional(readOnly = true)
  public NotificationSettingsResDto getSettings(AuthenticatedUser user) {
    UUID userId = requireUserId(user);
    Map<NotificationType, Boolean> overrides =
        preferenceRepository.findByUserId(userId).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    UserNotificationPreference::getNotificationType,
                    UserNotificationPreference::getEnabled));
    java.util.Map<String, Boolean> settings = new java.util.LinkedHashMap<>();
    for (NotificationType type : NotificationType.values()) {
      settings.put(type.name(), overrides.getOrDefault(type, Boolean.TRUE));
    }
    return new NotificationSettingsResDto(settings);
  }

  /** 알림 유형별 수신 여부를 부분 갱신한다. */
  @Transactional
  public NotificationSettingsResDto updateSettings(
      AuthenticatedUser user, Map<String, Boolean> patch) {
    UUID userId = requireUserId(user);
    if (patch != null) {
      patch.forEach(
          (typeName, enabled) -> {
            NotificationType type = parseType(typeName);
            preferenceRepository
                .findByUserIdAndNotificationType(userId, type)
                .ifPresentOrElse(
                    pref -> pref.setEnabled(Boolean.TRUE.equals(enabled)),
                    () ->
                        preferenceRepository.save(
                            UserNotificationPreference.builder()
                                .userId(userId)
                                .notificationType(type)
                                .enabled(Boolean.TRUE.equals(enabled))
                                .build()));
          });
    }
    return getSettings(user);
  }

  /** 운영자가 구역 Topic 또는 전체에 즉시 푸시한다. */
  @Transactional
  public PushLogResDto operatorPush(
      AuthenticatedUser user, String targetType, String zoneId, String title, String body) {
    operatorAuthorization.requireOperator(user);
    List<String> tokens;
    String target;
    if ("ZONE_TOPIC".equals(targetType)) {
      String zone = parseZone(zoneId);
      List<UUID> userIds =
          subscriptionRepository.findByZoneId(zone).stream()
              .map(UserZoneSubscription::getUserId)
              .toList();
      tokens = tokensOf(userIds);
      target = "zone." + zone;
    } else {
      tokens = deviceTokenRepository.findAll().stream().map(UserDeviceToken::getFcmToken).toList();
      target = "ALL";
    }
    int sent = pushSender.send(tokens, title, body);
    PushNotificationLog logEntry =
        pushLogRepository.save(
            PushNotificationLog.builder()
                .kind("OPERATOR")
                .target(target)
                .payload(Map.of("title", title, "body", body))
                .recipientCount(sent)
                .result("SENT")
                .build());
    return new PushLogResDto(logEntry.getId().toString(), sent);
  }

  private List<String> tokensOf(List<UUID> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return deviceTokenRepository.findByUserIdIn(userIds).stream()
        .map(UserDeviceToken::getFcmToken)
        .toList();
  }

  private DevicePlatform parsePlatform(String platform) {
    try {
      return DevicePlatform.valueOf(platform.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_state");
    }
  }

  private NotificationType parseType(String type) {
    try {
      return NotificationType.valueOf(type.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_state");
    }
  }

  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
