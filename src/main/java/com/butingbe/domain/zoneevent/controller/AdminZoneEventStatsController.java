package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventStatsResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventStatsService;
import com.butingbe.global.common.ApiResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회차·슬롯별 운영 통계. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-stats")
@RequiredArgsConstructor
public class AdminZoneEventStatsController {

  private final AdminZoneEventStatsService adminZoneEventStatsService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventStatsResDto>> stats(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) UUID roundId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {
    return ResponseEntity.ok(
        ApiResponse.success("운영 통계", adminZoneEventStatsService.stats(user, roundId, from, to)));
  }
}
