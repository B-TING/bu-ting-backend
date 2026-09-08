package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventUpdateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AuthTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RewardSnapshotReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 이벤트·인증 타겟 관리와 상태 전환. 모든 메서드는 ROLE_ADMIN/MANAGER만 호출할 수 있다. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;
  private static final List<Character> SLOT_LETTERS = List.of('A', 'B', 'C', 'D');

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventTypeRepository zoneEventTypeRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final RewardCatalogRepository rewardCatalogRepository;
  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional
  public AdminZoneEventResDto create(AuthenticatedUser user, AdminZoneEventCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    String zoneId = parseZone(request.zoneId());
    ZoneEventType type = requireType(request.typeCode());
    validateRewardCodes(request.baseReward(), request.excellenceReward());

    OffsetDateTime endsAt = request.startsAt().plusMinutes(request.durationMinutes());
    requireNoOverlap(zoneId, request.startsAt(), endsAt, null);

    ZoneEventRound round = null;
    String slotCode = null;
    if (request.roundId() != null) {
      ZoneEventRound draft =
          roundRepository
              .findById(request.roundId())
              .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
      if (draft.getStatus() != RoundStatus.DRAFT) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
      // 취소된 이벤트는 그 구역을 계속 점유하지 않는다. 취소 후 같은 구역에 대체 이벤트를 넣을 수 있어야 한다.
      List<ZoneEvent> existing =
          zoneEventRepository.findByRoundId(draft.getId()).stream()
              .filter(e -> e.getStatus() != ZoneEventStatus.CANCELLED)
              .toList();
      if (existing.stream().anyMatch(e -> e.getZoneId().equals(zoneId))) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
      if (existing.size() >= 4) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
      // (round_id, slot_code) 유니크 인덱스가 있으므로 아직 쓰이지 않은 첫 글자를 고른다. 취소된 이벤트는
      // markCancelled()에서 코드를 반납하므로, 취소된 구역을 다시 채울 때 그 글자가 재사용된다.
      List<String> usedCodes = existing.stream().map(ZoneEvent::getSlotCode).toList();
      slotCode =
          SLOT_LETTERS.stream()
              .map(letter -> draft.getRoundNo() + "-" + letter)
              .filter(code -> !usedCodes.contains(code))
              .toList()
              .get(0);
      round = draft;
    }

    RewardSnapshotReqDto excellence =
        request.excellenceReward() != null
            ? request.excellenceReward()
            : (round != null && round.getExcellenceReward() != null
                ? toReqDto(round.getExcellenceReward())
                : null);

    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId(zoneId)
                .type(type)
                .roundId(request.roundId())
                .slotCode(slotCode)
                .title(request.title())
                .description(request.description())
                .startsAt(request.startsAt())
                .durationMinutes(request.durationMinutes())
                .status(ZoneEventStatus.SCHEDULED)
                .baseReward(request.baseReward().toSnapshot())
                .excellenceReward(excellence == null ? null : excellence.toSnapshot())
                .successLimitPerUser(request.successLimitPerUser())
                .build());

    if (round != null) {
      // (round_id, zone_id) UK가 있으므로, 취소된 이벤트가 남긴 슬롯은 새로 만들지 말고 재사용한다.
      ZoneEventRound slotRound = round;
      ZoneEventRoundSlot slot =
          slotRepository
              .findByRound_IdAndZoneId(slotRound.getId(), zoneId)
              .orElseGet(
                  () ->
                      slotRepository.save(
                          ZoneEventRoundSlot.builder()
                              .round(slotRound)
                              .slotKind(SlotKind.AUTH)
                              .zoneId(zoneId)
                              .build()));
      slot.assignEvent(event.getId());
    }

    ZoneEventAuthTarget target = null;
    if (Boolean.TRUE.equals(type.getRequiresUpload())) {
      if (request.authTarget() == null) {
        throw new IllegalArgumentException("error.zone_event.media.invalid");
      }
      target = authTargetRepository.save(buildTarget(event, request.authTarget()));
    } else if (request.authTarget() != null) {
      target = authTargetRepository.save(buildTarget(event, request.authTarget()));
    }
    audit(user, "CREATE_EVENT", "EVENT", event.getId(), Map.of("zoneId", zoneId));
    return AdminZoneEventResDto.of(event, target, 0, 0);
  }

  private RewardSnapshotReqDto toReqDto(RewardSnapshot snapshot) {
    return new RewardSnapshotReqDto(
        snapshot.points(), snapshot.badgeCode(), snapshot.topN(), snapshot.prizeRewardCode());
  }

  private void requireNoOverlap(
      String zoneId, OffsetDateTime startsAt, OffsetDateTime endsAt, UUID excludeEventId) {
    List<ZoneEventStatus> blocking = List.of(ZoneEventStatus.SCHEDULED, ZoneEventStatus.ACTIVE);
    for (ZoneEvent existing : zoneEventRepository.findByZoneIdAndStatusIn(zoneId, blocking)) {
      if (excludeEventId != null && existing.getId().equals(excludeEventId)) {
        continue;
      }
      boolean overlaps =
          existing.getStartsAt().isBefore(endsAt) && startsAt.isBefore(existing.endsAt());
      if (overlaps) {
        throw new ConflictException("error.zone_event.invalid_state");
      }
    }
  }

  @Transactional(readOnly = true)
  public AdminZoneEventPageResDto list(
      AuthenticatedUser user,
      UUID roundId,
      String zone,
      String status,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    String zoneId = zone == null || zone.isBlank() ? null : parseZone(zone);
    ZoneEventStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    int pageNumber = page == null || page < 0 ? 0 : page;

    Specification<ZoneEvent> spec = buildListSpec(roundId, zoneId, statusFilter, from, to);
    org.springframework.data.domain.Page<ZoneEvent> result =
        zoneEventRepository.findAll(
            spec,
            PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id"))));

    List<AdminZoneEventResDto> items = result.getContent().stream().map(this::toDetail).toList();
    return new AdminZoneEventPageResDto(
        items, pageNumber, pageSize, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AdminZoneEventResDto detail(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    return toDetail(findEvent(eventId));
  }

  @Transactional
  public AdminZoneEventResDto update(
      AuthenticatedUser user, UUID eventId, AdminZoneEventUpdateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);

    if (!event.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (event.getStatus() == ZoneEventStatus.ACTIVE && request.touchesScheduledOnlyFields()) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (request.touchesTimeOrZone()) {
      String overlapZoneId =
          request.zoneId() == null ? event.getZoneId() : parseZone(request.zoneId());
      OffsetDateTime overlapStartsAt =
          request.startsAt() == null ? event.getStartsAt() : request.startsAt();
      int overlapDuration =
          request.durationMinutes() == null
              ? event.getDurationMinutes()
              : request.durationMinutes();
      requireNoOverlap(
          overlapZoneId,
          overlapStartsAt,
          overlapStartsAt.plusMinutes(overlapDuration),
          event.getId());
    }

    RewardSnapshotReqDto base = request.baseReward();
    validateRewardCodes(base, request.excellenceReward());
    event.applyEditable(
        request.title(),
        request.description(),
        request.durationMinutes(),
        request.successLimitPerUser(),
        request.excellenceReward() == null ? null : request.excellenceReward().toSnapshot(),
        request.excellenceReward() != null);

    if (request.touchesScheduledOnlyFields()) {
      String zoneId = request.zoneId() == null ? null : parseZone(request.zoneId());
      ZoneEventType type = request.typeCode() == null ? null : requireType(request.typeCode());
      event.applyScheduledOnly(
          zoneId, type, request.startsAt(), base == null ? null : base.toSnapshot());
    }

    if (request.authTarget() != null) {
      authTargetRepository
          .findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(eventId, ZoneEventTargetStatus.ACTIVE)
          .ifPresent(
              target ->
                  target.update(
                      request.authTarget().placeName(),
                      request.authTarget().guideText(),
                      request.authTarget().exampleFileKey(),
                      request.authTarget().latitude(),
                      request.authTarget().longitude(),
                      request.authTarget().radiusM()));
    }
    audit(
        user,
        "PATCH_EVENT",
        "EVENT",
        eventId,
        request.reason() == null ? null : Map.of("reason", request.reason()));
    return toDetail(event);
  }

  @Transactional
  public AdminZoneEventResDto cancel(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    event.markCancelled();
    // BR-13: 열린 참여는 EVENT_CANCELLED로 정리하고, 성공한 참여와 보상은 유지한다.
    for (ZoneEventParticipation open :
        participationRepository.findByEvent_IdAndStatusIn(
            eventId,
            List.of(
                ParticipationStatus.JOINED,
                ParticipationStatus.SUBMITTED,
                ParticipationStatus.UNDER_REVIEW))) {
      open.cancel("EVENT_CANCELLED");
    }
    audit(user, "CANCEL_EVENT", "EVENT", eventId, null);
    return toDetail(event);
  }

  private void audit(
      AuthenticatedUser user,
      String action,
      String targetType,
      UUID targetId,
      Map<String, Object> detail) {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(action)
            .targetType(targetType)
            .targetId(targetId)
            .detail(detail)
            .build());
  }

  private AdminZoneEventResDto toDetail(ZoneEvent event) {
    ZoneEventAuthTarget target =
        authTargetRepository
            .findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
                event.getId(), ZoneEventTargetStatus.ACTIVE)
            .orElse(null);
    long joined = participationRepository.countByEvent_Id(event.getId());
    long success =
        participationRepository.countByEvent_IdAndStatus(
            event.getId(), ParticipationStatus.SUCCESS);
    return AdminZoneEventResDto.of(event, target, joined, success);
  }

  private Specification<ZoneEvent> buildListSpec(
      UUID roundId, String zoneId, ZoneEventStatus status, OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (roundId != null) {
        predicates.add(cb.equal(root.get("roundId"), roundId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("zoneId"), zoneId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private ZoneEventAuthTarget buildTarget(ZoneEvent event, AuthTargetReqDto request) {
    return ZoneEventAuthTarget.builder()
        .event(event)
        .targetKind(parseTargetKind(request.targetKind()))
        .landmarkId(request.landmarkId())
        .placeName(request.placeName())
        .guideText(request.guideText())
        .exampleFileKey(request.exampleFileKey())
        .latitude(request.latitude())
        .longitude(request.longitude())
        .radiusM(request.radiusM())
        .build();
  }

  private void validateRewardCodes(RewardSnapshotReqDto base, RewardSnapshotReqDto excellence) {
    if (base != null
        && base.badgeCode() != null
        && !rewardCatalogRepository.existsByCode(base.badgeCode())) {
      throw new IllegalArgumentException("error.reward.catalog_not_found");
    }
    if (excellence != null
        && excellence.prizeRewardCode() != null
        && !rewardCatalogRepository.existsByCode(excellence.prizeRewardCode())) {
      throw new IllegalArgumentException("error.reward.catalog_not_found");
    }
  }

  private ZoneEvent findEvent(UUID eventId) {
    return zoneEventRepository
        .findById(eventId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private ZoneEventType requireType(String typeCode) {
    return zoneEventTypeRepository
        .findById(typeCode)
        .orElseThrow(() -> new IllegalArgumentException("error.zone_event.type_not_found"));
  }

  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
  }

  private ZoneEventStatus parseStatus(String status) {
    try {
      return ZoneEventStatus.valueOf(status.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_state");
    }
  }

  private ZoneEventTargetKind parseTargetKind(String kind) {
    try {
      return ZoneEventTargetKind.valueOf(kind.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.media.invalid");
    }
  }
}
