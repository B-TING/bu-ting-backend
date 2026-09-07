package com.butingbe.domain.notification.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.notification.dto.request.DeviceTokenReqDto;
import com.butingbe.domain.notification.dto.request.ZoneSubscriptionReqDto;
import com.butingbe.domain.notification.dto.response.NotificationSettingsResDto;
import com.butingbe.domain.notification.dto.response.ZoneSubscriptionsResDto;
import com.butingbe.domain.notification.service.NotificationService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 유저 알림 설정: 디바이스 토큰·관심 구역·수신 유형. 로그인 필요. */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserNotificationController {

  private final NotificationService notificationService;

  @PutMapping("/device-tokens")
  public ResponseEntity<ApiResponse<Void>> registerToken(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid DeviceTokenReqDto request) {
    notificationService.upsertToken(user, request.fcmToken(), request.platform());
    return ResponseEntity.ok(ApiResponse.success("디바이스 토큰 등록", null));
  }

  @DeleteMapping("/device-tokens/{fcmToken}")
  public ResponseEntity<Void> deleteToken(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String fcmToken) {
    notificationService.deleteToken(user, fcmToken);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/zone-subscriptions")
  public ResponseEntity<ApiResponse<ZoneSubscriptionsResDto>> getSubscriptions(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(
        ApiResponse.success("관심 구역 조회", notificationService.getSubscriptions(user)));
  }

  @PutMapping("/zone-subscriptions")
  public ResponseEntity<ApiResponse<ZoneSubscriptionsResDto>> setSubscriptions(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody ZoneSubscriptionReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "관심 구역 설정", notificationService.setSubscriptions(user, request.zoneIds())));
  }

  @GetMapping("/notification-settings")
  public ResponseEntity<ApiResponse<NotificationSettingsResDto>> getSettings(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(
        ApiResponse.success("알림 설정 조회", notificationService.getSettings(user)));
  }

  @PatchMapping("/notification-settings")
  public ResponseEntity<ApiResponse<NotificationSettingsResDto>> updateSettings(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestBody Map<String, Boolean> request) {
    return ResponseEntity.ok(
        ApiResponse.success("알림 설정 변경", notificationService.updateSettings(user, request)));
  }
}
