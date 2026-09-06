package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.ParticipationJoinReqDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventParticipationService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이벤트 참여 시작. 로그인 필요. */
@RestController
@RequestMapping("/zone-events/{eventId}/participations")
@RequiredArgsConstructor
public class ZoneEventParticipationController {

  private final ZoneEventParticipationService participationService;

  @PostMapping
  public ResponseEntity<ApiResponse<ParticipationResDto>> join(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @RequestBody @Valid ParticipationJoinReqDto request) {
    ParticipationResDto participation =
        participationService.join(user, eventId, request.latitude(), request.longitude());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("이벤트 참여 시작", participation));
  }
}
