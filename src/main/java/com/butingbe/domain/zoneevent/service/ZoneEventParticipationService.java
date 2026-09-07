package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.exception.OpenParticipationExistsException;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.support.GpsDistance;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 참여 시작.
 *
 * <p>현재 GPS가 타겟 반경 이내일 때만 JOINED 참여를 만든다. 유저·이벤트당 열린 참여는 하나이고(부분 UK, NFR-02), 성공 상한을 넘기면 새 참여를 막는다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventParticipationService {

  private static final List<ParticipationStatus> OPEN_STATUSES =
      List.of(
          ParticipationStatus.JOINED,
          ParticipationStatus.SUBMITTED,
          ParticipationStatus.UNDER_REVIEW);

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventParticipationRepository participationRepository;

  /** 반경 검증을 통과하면 JOINED 참여를 만들어 돌려준다. */
  @Transactional
  public ParticipationResDto join(
      AuthenticatedUser user, UUID eventId, double latitude, double longitude) {
    UUID userId = requireUserId(user);
    ZoneEvent event =
        zoneEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    if (event.getStatus() != ZoneEventStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.not_active");
    }

    ZoneEventAuthTarget target =
        authTargetRepository
            .findByEvent_Id(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    int distance =
        GpsDistance.meters(latitude, longitude, target.getLatitude(), target.getLongitude());
    if (distance > target.getRadiusM()) {
      throw new ZoneEventOutOfRangeException(distance);
    }

    participationRepository
        .findByEvent_IdAndUserIdAndStatusIn(eventId, userId, OPEN_STATUSES)
        .ifPresent(
            open -> {
              throw new OpenParticipationExistsException(open.getId());
            });

    long successes =
        participationRepository.countByEvent_IdAndUserIdAndStatus(
            eventId, userId, ParticipationStatus.SUCCESS);
    if (successes >= event.getSuccessLimitPerUser()) {
      throw new ConflictException("error.zone_event.participation.limit_reached");
    }

    ZoneEventParticipation saved;
    try {
      saved =
          participationRepository.save(
              ZoneEventParticipation.join(event, userId, latitude, longitude));
    } catch (DataIntegrityViolationException concurrent) {
      // 부분 UK 위반: 동시 요청이 먼저 열린 참여를 만들었다.
      UUID existing =
          participationRepository
              .findByEvent_IdAndUserIdAndStatusIn(eventId, userId, OPEN_STATUSES)
              .map(ZoneEventParticipation::getId)
              .orElse(null);
      throw new OpenParticipationExistsException(existing);
    }

    return ParticipationResDto.of(saved, distance);
  }

  /** 열린 참여를 취소한다. SUCCESS/FAIL/REVOKED/이미 취소된 참여는 취소할 수 없다. */
  @Transactional
  public void cancel(AuthenticatedUser user, UUID eventId, UUID participationId) {
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
    if (!participation.getStatus().isOpen()) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    participation.cancel("USER");
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
