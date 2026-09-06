package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.zoneevent.dto.request.ParticipationSubmitReqDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.support.GpsDistance;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 제출과 판정.
 *
 * <p>제출을 모두 검증한 뒤 판정 모드에 따라 처리한다. AUTO면 같은 트랜잭션에서 SUCCESS로 확정하고 기본 보상을 지급한다(FR-RWD-01).
 * MANUAL/HYBRID면 검수 대기로 보낸다. 반경은 참여 시작에 이어 제출 시점에도 다시 검증한다(BR-05).
 */
@Service
@RequiredArgsConstructor
public class ZoneEventSubmitService {

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final FileMetadataRepository fileMetadataRepository;
  private final RewardService rewardService;
  private final UserPointService userPointService;
  private final ZoneTitleService zoneTitleService;

  @Value("${zone-event.review.mode:AUTO}")
  private String reviewMode;

  @Value("${zone-event.review.captured-at-threshold-minutes:10}")
  private long capturedAtThresholdMinutes;

  @Transactional
  public SubmitResultResDto submit(
      AuthenticatedUser user,
      UUID eventId,
      UUID participationId,
      ParticipationSubmitReqDto request) {
    UUID userId = requireUserId(user);

    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .filter(p -> p.getEvent().getId().equals(eventId))
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));

    if (!participation.getUserId().equals(userId)) {
      throw new ForbiddenException("error.zone_event.participation.forbidden");
    }
    if (participation.getStatus() != ParticipationStatus.JOINED) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }

    ZoneEvent event = participation.getEvent();
    if (event.getStatus() != ZoneEventStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.not_active");
    }

    ZoneEventAuthTarget target =
        authTargetRepository
            .findByEvent_Id(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    int distance =
        GpsDistance.meters(
            request.latitude(), request.longitude(), target.getLatitude(), target.getLongitude());
    if (distance > target.getRadiusM()) {
      throw new ZoneEventOutOfRangeException(distance);
    }

    validateMedia(request.mediaFileKey());

    participation.submit(
        request.mediaFileKey(),
        request.content(),
        request.latitude(),
        request.longitude(),
        request.capturedAt());

    if (isAutoApprove() && !capturedTooOld(request.capturedAt())) {
      participation.markSuccess();
      RewardSnapshot base = event.getBaseReward();
      BaseRewardResult reward =
          rewardService.grantBaseReward(
              userId,
              participationId,
              eventId,
              base == null ? null : base.points(),
              base == null ? null : base.badgeCode());
      List<Object> newlyEarnedTitles =
          new java.util.ArrayList<>(zoneTitleService.awardTitles(userId, event.getZoneId()));
      return SubmitResultResDto.of(
          ParticipationResDto.of(participation, null),
          reward.rewards(),
          reward.pointBalance(),
          newlyEarnedTitles);
    }

    participation.markUnderReview();
    return SubmitResultResDto.of(
        ParticipationResDto.of(participation, null),
        List.of(),
        userPointService.getBalance(userId));
  }

  /** fileKey가 등록된 이미지인지 확인한다. 업로더 검증은 FileMetadata가 업로더를 저장하지 않아 보류한다. */
  private void validateMedia(String mediaFileKey) {
    FileMetadata file =
        fileMetadataRepository
            .findByObjectKey(mediaFileKey)
            .orElseThrow(() -> new IllegalArgumentException("error.zone_event.media.invalid"));
    if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
      throw new IllegalArgumentException("error.zone_event.media.invalid");
    }
  }

  /** 촬영 시각과 서버 수신 시각 차이가 임계치를 넘으면 자동 성공 대신 검수로 보낸다(FR-PTC-09). */
  private boolean capturedTooOld(java.time.OffsetDateTime capturedAt) {
    return capturedAt != null
        && java.time.Duration.between(capturedAt, java.time.OffsetDateTime.now()).toMinutes()
            > capturedAtThresholdMinutes;
  }

  private boolean isAutoApprove() {
    return reviewMode == null || "AUTO".equalsIgnoreCase(reviewMode);
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
