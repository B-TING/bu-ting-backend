package com.butingbe.domain.notification.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.notification.dto.request.AdminPushReqDto;
import com.butingbe.domain.notification.dto.response.PushLogResDto;
import com.butingbe.domain.notification.service.NotificationService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 즉시 푸시. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/push")
@RequiredArgsConstructor
public class AdminPushController {

  private final NotificationService notificationService;

  @PostMapping
  public ResponseEntity<ApiResponse<PushLogResDto>> push(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid AdminPushReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "푸시 발송",
                notificationService.operatorPush(
                    user,
                    request.targetType(),
                    request.zoneId(),
                    request.title(),
                    request.body())));
  }
}
