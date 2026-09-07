package com.butingbe.domain.reward.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.request.AdminRewardCatalogCreateReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardCatalogUpdateReqDto;
import com.butingbe.domain.reward.dto.response.AdminRewardGrantPageResDto;
import com.butingbe.domain.reward.dto.response.RewardCatalogResDto;
import com.butingbe.domain.reward.service.AdminRewardCatalogService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영 보상 카탈로그 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/reward-catalog")
@RequiredArgsConstructor
public class AdminRewardCatalogController {

  private final AdminRewardCatalogService adminRewardCatalogService;

  @PostMapping
  public ResponseEntity<ApiResponse<RewardCatalogResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid AdminRewardCatalogCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("보상 카탈로그 생성", adminRewardCatalogService.create(user, request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<RewardCatalogResDto>>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String rewardType,
      @RequestParam(required = false) Boolean active) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "보상 카탈로그 조회", adminRewardCatalogService.list(user, rewardType, active)));
  }

  @PatchMapping("/{rewardId}")
  public ResponseEntity<ApiResponse<RewardCatalogResDto>> update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID rewardId,
      @RequestBody AdminRewardCatalogUpdateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "보상 카탈로그 수정", adminRewardCatalogService.update(user, rewardId, request)));
  }

  @GetMapping("/{rewardId}/grants")
  public ResponseEntity<ApiResponse<AdminRewardGrantPageResDto>> grants(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID rewardId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "지급 이력 조회", adminRewardCatalogService.grants(user, rewardId, cursor, size)));
  }
}
