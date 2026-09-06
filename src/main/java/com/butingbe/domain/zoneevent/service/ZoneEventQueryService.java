package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventSummaryResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 이벤트 조회. 비로그인도 허용하며, 로그인 시에만 개인화 필드(내 참여 상태·남은 참여 가능 횟수)를 채운다.
 *
 * <p>열린 참여를 판별하는 상태 집합은 {@link ParticipationStatus#isOpen()}과 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneEventQueryService {

  private static final List<ParticipationStatus> OPEN_STATUSES =
      List.of(
          ParticipationStatus.JOINED,
          ParticipationStatus.SUBMITTED,
          ParticipationStatus.UNDER_REVIEW);

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final FileStorageService fileStorageService;

  /** 구역의 활성 이벤트 목록. userId가 있으면 내 참여 상태를 함께 채운다. */
  public List<ZoneEventSummaryResDto> getActiveEvents(String zone, UUID userId) {
    String zoneId = parseZone(zone);
    OffsetDateTime now = OffsetDateTime.now();
    return zoneEventRepository
        .findByZoneIdAndStatusOrderByStartsAtAsc(zoneId, ZoneEventStatus.ACTIVE)
        .stream()
        .map(event -> toSummary(event, now, userId))
        .toList();
  }

  /** 이벤트 상세. 없는 이벤트는 404. */
  public ZoneEventDetailResDto getEventDetail(UUID eventId, UUID userId) {
    ZoneEvent event =
        zoneEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    ZoneEventAuthTarget target = authTargetRepository.findByEvent_Id(eventId).orElse(null);
    OffsetDateTime now = OffsetDateTime.now();

    long successCount =
        participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.SUCCESS);
    String exampleImageUrl =
        target == null || target.getExampleFileKey() == null
            ? null
            : fileStorageService.getPresignedUrl(target.getExampleFileKey());
    Integer myRemainingAttempts = userId == null ? null : remainingAttempts(event, userId);

    return ZoneEventDetailResDto.of(
        event,
        target,
        exampleImageUrl,
        remainingSeconds(event, now),
        successCount,
        myRemainingAttempts);
  }

  private ZoneEventSummaryResDto toSummary(ZoneEvent event, OffsetDateTime now, UUID userId) {
    ZoneEventAuthTarget target = authTargetRepository.findByEvent_Id(event.getId()).orElse(null);
    long successCount =
        participationRepository.countByEvent_IdAndStatus(
            event.getId(), ParticipationStatus.SUCCESS);

    String myStatus = null;
    UUID myOpenParticipationId = null;
    if (userId != null) {
      Optional<ZoneEventParticipation> open =
          participationRepository.findByEvent_IdAndUserIdAndStatusIn(
              event.getId(), userId, OPEN_STATUSES);
      if (open.isPresent()) {
        myStatus = open.get().getStatus().name();
        myOpenParticipationId = open.get().getId();
      }
    }

    return ZoneEventSummaryResDto.of(
        event, target, remainingSeconds(event, now), successCount, myStatus, myOpenParticipationId);
  }

  private Integer remainingAttempts(ZoneEvent event, UUID userId) {
    long successes =
        participationRepository.countByEvent_IdAndUserIdAndStatus(
            event.getId(), userId, ParticipationStatus.SUCCESS);
    return Math.max(0, event.getSuccessLimitPerUser() - (int) successes);
  }

  private long remainingSeconds(ZoneEvent event, OffsetDateTime now) {
    long seconds = java.time.Duration.between(now, event.endsAt()).getSeconds();
    return Math.max(0, seconds);
  }

  /** ChatZone enum으로 검증한다. 잘못된 값이면 400(error.zone_event.invalid_zone). */
  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
  }
}
