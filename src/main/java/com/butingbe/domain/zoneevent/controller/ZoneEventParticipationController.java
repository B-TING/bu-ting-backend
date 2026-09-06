package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.ParticipationJoinReqDto;
import com.butingbe.domain.zoneevent.dto.request.ParticipationSubmitReqDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventParticipationQueryService;
import com.butingbe.domain.zoneevent.service.ZoneEventParticipationService;
import com.butingbe.domain.zoneevent.service.ZoneEventSubmitService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이벤트 참여 시작·제출·취소·이벤트별 내 참여. 로그인 필요. */
@RestController
@RequestMapping("/zone-events/{eventId}/participations")
@RequiredArgsConstructor
public class ZoneEventParticipationController {

  private final ZoneEventParticipationService participationService;
  private final ZoneEventSubmitService submitService;
  private final ZoneEventParticipationQueryService participationQueryService;

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

  @PostMapping("/{participationId}/submit")
  public ResponseEntity<ApiResponse<SubmitResultResDto>> submit(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID participationId,
      @RequestBody @Valid ParticipationSubmitReqDto request) {
    SubmitResultResDto result = submitService.submit(user, eventId, participationId, request);
    return ResponseEntity.ok(ApiResponse.success("인증 제출", result));
  }

  @DeleteMapping("/{participationId}")
  public ResponseEntity<Void> cancel(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID eventId,
      @PathVariable UUID participationId) {
    participationService.cancel(user, eventId, participationId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<List<ParticipationResDto>>> myParticipations(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID eventId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "이벤트별 내 참여 조회", participationQueryService.myEventParticipations(user, eventId)));
  }
}
