package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.ReportDismissReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDecisionResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventReportService;
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

/** 신고 검수 큐·상세·인정·기각. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-reports")
@RequiredArgsConstructor
public class AdminZoneEventReportController {

  private final AdminZoneEventReportService adminZoneEventReportService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventReportPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) UUID eventId,
      @RequestParam(required = false) UUID participationId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "신고 목록",
            adminZoneEventReportService.list(
                user, status, roundId, eventId, participationId, page, size)));
  }

  @GetMapping("/{reportId}")
  public ResponseEntity<ApiResponse<AdminZoneEventReportDetailResDto>> detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reportId) {
    return ResponseEntity.ok(
        ApiResponse.success("신고 상세", adminZoneEventReportService.detail(user, reportId)));
  }

  @PostMapping("/{reportId}/uphold")
  public ResponseEntity<ApiResponse<AdminZoneEventReportDecisionResDto>> uphold(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID reportId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReportUpholdReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "신고 인정", adminZoneEventReportService.uphold(user, reportId, request, idempotencyKey)));
  }

  @PostMapping("/{reportId}/dismiss")
  public ResponseEntity<ApiResponse<AdminZoneEventReportDecisionResDto>> dismiss(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID reportId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReportDismissReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "신고 기각", adminZoneEventReportService.dismiss(user, reportId, request, idempotencyKey)));
  }
}
