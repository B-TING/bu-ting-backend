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
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zoneevent.support.GpsDistance;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 제출과 판정.
 *
 * <p>제출을 모두 검증한 뒤 판정 모드에 따라 처리한다. AUTO면 같은 트랜잭션에서 SUCCESS로 확정하고 기본 보상을 지급한다(FR-RWD-01).
 * MANUAL/HYBRID면 검수 대기로 보낸다. 반경은 참여 시작에 이어 제출 시점에도 다시 검증한다(BR-05).
 *
 * <p>참여가 JOINED거나(최초 제출) FAIL이면(반려 후 재제출) 제출을 받는다. 매 호출마다 새 {@link ZoneEventSubmission} row를 만들고 이전
 * 이력은 바꾸지 않는다. 이벤트 마감({@code endsAt}) 이후에는 최초 제출도 재제출도 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventSubmitService {

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final FileMetadataRepository fileMetadataRepository;
  private final RewardService rewardService;
  private final UserPointService userPointService;
  private final ZoneTitleService zoneTitleService;

  @Value("${zone-event.review.mode:AUTO}")
  private String reviewMode;

  @Value("${zone-event.review.captured-at-threshold-minutes:10}")
  private long capturedAtThresholdMinutes;

  @Value("${zone-event.review.upload-recency-threshold-minutes:30}")
  private long uploadRecencyThresholdMinutes;

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
    if (participation.getStatus() != ParticipationStatus.JOINED
        && participation.getStatus() != ParticipationStatus.FAIL) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }

    ZoneEvent event = participation.getEvent();
    if (event.getStatus() != ZoneEventStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.not_active");
    }
    if (!OffsetDateTime.now().isBefore(event.endsAt())) {
      throw new ConflictException("error.zone_event.ended");
    }

    ZoneEventAuthTarget target = requireActiveTarget(eventId, request.targetId());
    int distance =
        GpsDistance.meters(
            request.latitude(), request.longitude(), target.getLatitude(), target.getLongitude());
    if (distance > target.getRadiusM()) {
      throw new ZoneEventOutOfRangeException(distance);
    }

    validateMedia(request.mediaFileKey(), userId);

    int attemptNo = (int) submissionRepository.countByParticipation_Id(participationId) + 1;
    ZoneEventSubmission submission;
    try {
      submission =
          submissionRepository.save(
              ZoneEventSubmission.builder()
                  .participation(participation)
                  .attemptNo(attemptNo)
                  .target(target)
                  .placeName(target.getPlaceName())
                  .targetLatitude(target.getLatitude())
                  .targetLongitude(target.getLongitude())
                  .radiusM(target.getRadiusM())
                  .guideTextSnapshot(target.getGuideText())
                  .mediaFileKey(request.mediaFileKey())
                  .gpsLat(request.latitude())
                  .gpsLng(request.longitude())
                  .capturedAt(request.capturedAt())
                  .build());
    } catch (DataIntegrityViolationException concurrent) {
      // media_file_key 유니크 인덱스 위반: 동시 요청이 같은 fileKey를 먼저 제출했다.
      throw new IllegalArgumentException("error.zone_event.media.already_used");
    }

    participation.submit(
        request.mediaFileKey(),
        request.content(),
        request.latitude(),
        request.longitude(),
        request.capturedAt());
    participation.linkSubmission(submission.getId());

    if (isAutoApprove() && !capturedTooOld(request.capturedAt())) {
      submission.approve(null);
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
          new ArrayList<>(zoneTitleService.awardTitles(userId, event.getZoneId()));
      return SubmitResultResDto.of(
          ParticipationResDto.of(participation, null),
          submission.getId().toString(),
          attemptNo,
          reward.rewards(),
          reward.pointBalance(),
          newlyEarnedTitles);
    }

    participation.markUnderReview();
    return SubmitResultResDto.of(
        ParticipationResDto.of(participation, null),
        submission.getId().toString(),
        attemptNo,
        List.of(),
        userPointService.getBalance(userId));
  }

  private ZoneEventAuthTarget requireActiveTarget(UUID eventId, UUID targetId) {
    ZoneEventAuthTarget target =
        authTargetRepository
            .findByIdAndEvent_Id(targetId, eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.target_not_found"));
    if (target.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ResourceNotFoundException("error.zone_event.target_not_found");
    }
    return target;
  }

  /** fileKey가 등록된 본인 소유의 이미지이고, 다른 제출에 쓰이지 않았으며, 업로드한 지 오래되지 않았는지 확인한다. */
  private void validateMedia(String mediaFileKey, UUID userId) {
    FileMetadata file =
        fileMetadataRepository
            .findByObjectKey(mediaFileKey)
            .orElseThrow(() -> new IllegalArgumentException("error.zone_event.media.invalid"));
    if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
      throw new IllegalArgumentException("error.zone_event.media.invalid");
    }
    if (file.getUploaderId() == null || !file.getUploaderId().equals(userId)) {
      throw new ForbiddenException("error.zone_event.media.forbidden");
    }
    if (submissionRepository.existsByMediaFileKey(mediaFileKey)) {
      throw new IllegalArgumentException("error.zone_event.media.already_used");
    }
    if (Duration.between(file.getCreatedAt(), LocalDateTime.now()).toMinutes()
        > uploadRecencyThresholdMinutes) {
      throw new IllegalArgumentException("error.zone_event.media.stale");
    }
  }

  /** 촬영 시각과 서버 수신 시각 차이가 임계치를 넘으면 자동 성공 대신 검수로 보낸다(FR-PTC-09). */
  private boolean capturedTooOld(OffsetDateTime capturedAt) {
    return capturedAt != null
        && Duration.between(capturedAt, OffsetDateTime.now()).toMinutes()
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
