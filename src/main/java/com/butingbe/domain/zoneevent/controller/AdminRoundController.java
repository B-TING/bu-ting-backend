package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCancelReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminTopNResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.service.AdminRoundConsoleService;
import com.butingbe.domain.zoneevent.service.AdminZoneEventWinnerService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Map;
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

/** 운영 회차 콘솔. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-rounds")
@RequiredArgsConstructor
public class AdminRoundController {

  private final AdminRoundConsoleService consoleService;
  private final AdminZoneEventWinnerService winnerService;

  @PostMapping
  public ResponseEntity<ApiResponse<AdminRoundResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid RoundCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("회차 초안 생성", consoleService.createRound(user, request)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<AdminRoundPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "회차 목록 조회", consoleService.listRounds(user, status, from, to, keyword, page, size)));
  }

  @GetMapping("/suggest-slots")
  public ResponseEntity<ApiResponse<SlotSuggestionResDto>> suggest(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "6") int authSlots) {
    return ResponseEntity.ok(
        ApiResponse.success("슬롯 배정 제안", consoleService.suggestSlots(user, authSlots)));
  }

  @GetMapping("/{roundId}")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(
        ApiResponse.success("회차 상세 조회", consoleService.roundDetail(user, roundId)));
  }

  @PatchMapping("/{roundId}")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> patch(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID roundId,
      @RequestBody @Valid RoundPatchReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("회차 수정", consoleService.patch(user, roundId, request)));
  }

  @PostMapping("/{roundId}/schedule")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> schedule(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(ApiResponse.success("회차 확정", consoleService.schedule(user, roundId)));
  }

  @PostMapping("/{roundId}/cancel")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> cancel(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID roundId,
      @RequestBody @Valid RoundCancelReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("회차 긴급 취소", consoleService.cancel(user, roundId, request)));
  }

  @PostMapping("/{roundId}/backup-targets")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> addBackupTarget(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID roundId,
      @RequestBody @Valid BackupTargetReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "예비 타겟 등록", consoleService.addBackupTarget(user, roundId, request)));
  }

  @PostMapping("/{roundId}/swap-target")
  public ResponseEntity<ApiResponse<AdminRoundResDto>> swapTarget(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID roundId,
      @RequestBody @Valid SwapTargetReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("우천 타겟 교체", consoleService.swapTarget(user, roundId, request)));
  }

  @PostMapping("/{roundId}/settle")
  public ResponseEntity<ApiResponse<Map<String, Object>>> settle(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(ApiResponse.success("회차 정산", consoleService.settle(user, roundId)));
  }

  @GetMapping("/{roundId}/settlement-report")
  public ResponseEntity<ApiResponse<Map<String, Object>>> settlementReport(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roundId) {
    return ResponseEntity.ok(
        ApiResponse.success("정산 리포트", consoleService.settlementReport(user, roundId)));
  }

  @GetMapping("/{roundId}/top-n")
  public ResponseEntity<ApiResponse<AdminTopNResDto>> topN(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID roundId,
      @RequestParam(required = false) UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success("Top N 경계 후보 조회", winnerService.topN(user, roundId, eventId)));
  }
}
