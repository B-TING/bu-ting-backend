package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.RejectReqDto;
import com.butingbe.domain.zoneevent.dto.response.ReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.service.AdminReviewService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영 검수 큐·처리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-participations")
@RequiredArgsConstructor
public class AdminReviewController {

  private final AdminReviewService adminReviewService;

  @GetMapping("/review-queue")
  public ResponseEntity<ApiResponse<ReviewQueuePageResDto>> reviewQueue(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success("검수 큐 조회", adminReviewService.reviewQueue(user, cursor, size)));
  }

  @PostMapping("/{participationId}/approve")
  public ResponseEntity<ApiResponse<SubmitResultResDto>> approve(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    return ResponseEntity.ok(
        ApiResponse.success("검수 승인", adminReviewService.approve(user, participationId)));
  }

  @PostMapping("/{participationId}/reject")
  public ResponseEntity<ApiResponse<Void>> reject(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestBody @Valid RejectReqDto request) {
    adminReviewService.reject(user, participationId, request.failReason());
    return ResponseEntity.ok(ApiResponse.success("검수 반려", null));
  }

  @PostMapping("/{participationId}/revoke")
  public ResponseEntity<ApiResponse<Void>> revoke(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    adminReviewService.revoke(user, participationId);
    return ResponseEntity.ok(ApiResponse.success("참여 회수", null));
  }

  @PostMapping("/{participationId}/unhide")
  public ResponseEntity<ApiResponse<Void>> unhide(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    adminReviewService.unhide(user, participationId);
    return ResponseEntity.ok(ApiResponse.success("숨김 해제", null));
  }
}
