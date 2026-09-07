package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventUpdateReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영 이벤트 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-events")
@RequiredArgsConstructor
public class AdminZoneEventController {

  private final AdminZoneEventService adminZoneEventService;

  @PostMapping
  public ResponseEntity<ApiResponse<AdminZoneEventResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid AdminZoneEventCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("이벤트 생성", adminZoneEventService.create(user, request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "이벤트 목록 조회", adminZoneEventService.list(user, zone, status, from, to, cursor, size)));
  }

  @GetMapping("/{eventId}")
  public ResponseEntity<ApiResponse<AdminZoneEventResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("이벤트 상세 조회", adminZoneEventService.detail(user, eventId)));
  }

  @PatchMapping("/{eventId}")
  public ResponseEntity<ApiResponse<AdminZoneEventResDto>> update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @RequestBody AdminZoneEventUpdateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("이벤트 수정", adminZoneEventService.update(user, eventId, request)));
  }

  @PostMapping("/{eventId}/activate")
  public ResponseEntity<ApiResponse<AdminZoneEventResDto>> activate(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("이벤트 활성화", adminZoneEventService.activate(user, eventId)));
  }

  @PostMapping("/{eventId}/close")
  public ResponseEntity<ApiResponse<AdminZoneEventResDto>> close(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("이벤트 종료", adminZoneEventService.close(user, eventId)));
  }

  @PostMapping("/{eventId}/cancel")
  public ResponseEntity<ApiResponse<AdminZoneEventResDto>> cancel(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("이벤트 취소", adminZoneEventService.cancel(user, eventId)));
  }
}
