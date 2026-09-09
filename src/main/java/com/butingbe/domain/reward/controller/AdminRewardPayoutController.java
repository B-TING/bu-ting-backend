package com.butingbe.domain.reward.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.request.ReleaseHoldReqDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutReleaseHoldResDto;
import com.butingbe.domain.reward.service.AdminRewardPayoutService;
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
import org.springframework.web.bind.annotation.RestController;

/** 신고로 보류된 지급의 최종 해제. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/reward-payouts")
@RequiredArgsConstructor
public class AdminRewardPayoutController {

  private final AdminRewardPayoutService adminRewardPayoutService;

  @GetMapping("/{payoutId}")
  public ResponseEntity<ApiResponse<AdminRewardPayoutDetailResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID payoutId) {
    return ResponseEntity.ok(
        ApiResponse.success("지급 건 상세", adminRewardPayoutService.detail(user, payoutId)));
  }

  @PostMapping("/{payoutId}/release-hold")
  public ResponseEntity<ApiResponse<AdminRewardPayoutReleaseHoldResDto>> releaseHold(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID payoutId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReleaseHoldReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "지급 보류 해제",
            adminRewardPayoutService.releaseHold(user, payoutId, request, idempotencyKey)));
  }
}
