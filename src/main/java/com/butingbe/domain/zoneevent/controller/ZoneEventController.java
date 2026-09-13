package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventSummaryResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventQueryService;
import com.butingbe.global.common.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구역 이벤트 조회. 비로그인도 허용하며 개인화 필드는 로그인 시에만 채워진다. */
@RestController
@RequestMapping("/zone-events")
@RequiredArgsConstructor
public class ZoneEventController {

  private final ZoneEventQueryService zoneEventQueryService;

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<List<ZoneEventSummaryResDto>>> getActiveEvents(
      @RequestParam String zone, @AuthenticationPrincipal AuthenticatedUser user) {
    List<ZoneEventSummaryResDto> events = zoneEventQueryService.getActiveEvents(zone, userId(user));
    return ResponseEntity.ok(ApiResponse.success("구역 활성 이벤트 조회", events));
  }

  @GetMapping("/{eventId}")
  public ResponseEntity<ApiResponse<ZoneEventDetailResDto>> getEventDetail(
      @PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser user) {
    ZoneEventDetailResDto detail = zoneEventQueryService.getEventDetail(eventId, userId(user));
    return ResponseEntity.ok(ApiResponse.success("이벤트 상세 조회", detail));
  }

  private UUID userId(AuthenticatedUser user) {
    return user == null ? null : user.id();
  }
}
