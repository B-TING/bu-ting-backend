package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.ReviewApproveReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReviewRejectReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDecisionResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventReviewService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사진 인증 검수 큐·상세·승인·반려. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-reviews")
@RequiredArgsConstructor
public class AdminZoneEventReviewController {

  private final AdminZoneEventReviewService adminZoneEventReviewService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminReviewQueuePageResDto>> queue(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) String zoneId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "검수 큐 조회", adminZoneEventReviewService.queue(user, roundId, eventId, zoneId, page, size)));
  }

  @GetMapping("/{participationId}")
  public ResponseEntity<ApiResponse<AdminReviewDetailResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    return ResponseEntity.ok(
        ApiResponse.success("검수 상세 조회", adminZoneEventReviewService.detail(user, participationId)));
  }

  @PostMapping("/{participationId}/approve")
  public ResponseEntity<ApiResponse<AdminReviewDecisionResDto>> approve(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReviewApproveReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "검수 승인",
            adminZoneEventReviewService.approve(user, participationId, request, idempotencyKey)));
  }

  @PostMapping("/{participationId}/reject")
  public ResponseEntity<ApiResponse<Void>> reject(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReviewRejectReqDto request) {
    adminZoneEventReviewService.reject(user, participationId, request, idempotencyKey);
    return ResponseEntity.ok(ApiResponse.success("검수 반려", null));
  }
}
