package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryPageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.service.ZoneEventParticipationQueryService;
import com.butingbe.global.common.ApiResponse;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 구역 이벤트 참여 이력. 로그인 필요. */
@RestController
@RequestMapping("/users/me/zone-event-participations")
@RequiredArgsConstructor
public class ZoneEventMeController {

  private final ZoneEventParticipationQueryService participationQueryService;

  @GetMapping
  public ResponseEntity<ApiResponse<ParticipationHistoryPageResDto>> history(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    ParticipationHistoryPageResDto page =
        participationQueryService.history(
            user, zone, type, parseStatuses(status), from, to, cursor, size);
    return ResponseEntity.ok(ApiResponse.success("내 참여 이력 조회", page));
  }

  /** 콤마로 구분된 상태 필터를 파싱한다. 잘못된 값은 400. */
  private List<ParticipationStatus> parseStatuses(String status) {
    if (status == null || status.isBlank()) {
      return List.of();
    }
    try {
      return Arrays.stream(status.split(","))
          .map(String::trim)
          .filter(value -> !value.isEmpty())
          .map(ParticipationStatus::valueOf)
          .toList();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.participation.invalid_state");
    }
  }
}
