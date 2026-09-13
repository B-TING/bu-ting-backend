package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetReplaceReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventTargetService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 타겟(선택 장소) 운영 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-events/{eventId}/targets")
@RequiredArgsConstructor
public class AdminZoneEventTargetController {

  private final AdminZoneEventTargetService adminZoneEventTargetService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminAuthTargetResDto>>> list(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("인증 타겟 목록", adminZoneEventTargetService.list(user, eventId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @RequestBody @Valid AdminAuthTargetCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "인증 타겟 추가", adminZoneEventTargetService.create(user, eventId, request)));
  }

  @PatchMapping("/{targetId}")
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> patch(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID targetId,
      @RequestBody @Valid AdminAuthTargetPatchReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "인증 타겟 수정", adminZoneEventTargetService.patch(user, eventId, targetId, request)));
  }

  @PostMapping("/{targetId}/replace")
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> replace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID targetId,
      @RequestBody @Valid AdminAuthTargetReplaceReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "인증 타겟 교체", adminZoneEventTargetService.replace(user, eventId, targetId, request)));
  }

  @PostMapping("/{targetId}/cancel")
  public ResponseEntity<ApiResponse<AdminAuthTargetResDto>> cancel(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID targetId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "인증 타겟 취소", adminZoneEventTargetService.cancel(user, eventId, targetId)));
  }
}
