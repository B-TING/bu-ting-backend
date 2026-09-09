package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.WinnerConfirmReqDto;
import com.butingbe.domain.zoneevent.dto.response.WinnerConfirmResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventWinnerService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이벤트 단위 수상자 확정·지급 후보 생성. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-events")
@RequiredArgsConstructor
public class AdminZoneEventWinnerController {

  private final AdminZoneEventWinnerService winnerService;

  @PostMapping("/{eventId}/winners/confirm")
  public ResponseEntity<ApiResponse<WinnerConfirmResDto>> confirmWinners(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid WinnerConfirmReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "수상자 확정", winnerService.confirmWinners(user, eventId, request, idempotencyKey)));
  }
}
