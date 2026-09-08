package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminParticipationPageResDto;
import com.butingbe.domain.zoneevent.service.AdminReviewService;
import com.butingbe.global.common.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 참여 회수·숨김 해제. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). 검수 큐/승인/반려는 {@code /admin/zone-event-reviews}로 이동했다. */
@RestController
@RequestMapping("/admin/zone-event-participations")
@RequiredArgsConstructor
public class AdminReviewController {

  private final AdminReviewService adminReviewService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminParticipationPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) String zoneId,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "전체 참여 목록",
            adminReviewService.list(
                user, roundId, eventId, zoneId, userId, status, keyword, page, size)));
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
