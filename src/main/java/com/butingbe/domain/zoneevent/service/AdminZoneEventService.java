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
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventTypeRepository zoneEventTypeRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final RewardCatalogRepository rewardCatalogRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional
  public AdminZoneEventResDto create(AuthenticatedUser user, AdminZoneEventCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    String zoneId = parseZone(request.zoneId());
    ZoneEventType type = requireType(request.typeCode());
    validateRewardCodes(request.baseReward(), request.excellenceReward());

    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId(zoneId)
                .type(type)
                .roundId(request.roundId())
                .title(request.title())
                .description(request.description())
                .startsAt(request.startsAt())
                .durationMinutes(request.durationMinutes())
                .status(ZoneEventStatus.SCHEDULED)
                .baseReward(request.baseReward().toSnapshot())
                .excellenceReward(
                    request.excellenceReward() == null
                        ? null
                        : request.excellenceReward().toSnapshot())
                .successLimitPerUser(request.successLimitPerUser())
                .build());

    ZoneEventAuthTarget target = null;
    if (Boolean.TRUE.equals(type.getRequiresUpload())) {
      if (request.authTarget() == null) {
        throw new IllegalArgumentException("error.zone_event.media.invalid");
      }
      target = authTargetRepository.save(buildTarget(event, request.authTarget()));
    } else if (request.authTarget() != null) {
      target = authTargetRepository.save(buildTarget(event, request.authTarget()));
    }
    return AdminZoneEventResDto.of(event, target, 0, 0);
  }

  @Transactional(readOnly = true)
  public AdminZoneEventPageResDto list(
      AuthenticatedUser user,
      String zone,
      String status,
      OffsetDateTime from,
      OffsetDateTime to,
      String cursor,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    String zoneId = zone == null || zone.isBlank() ? null : parseZone(zone);
    ZoneEventStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);

    Specification<ZoneEvent> spec = buildListSpec(zoneId, statusFilter, from, to, decoded);
    List<ZoneEvent> rows =
        zoneEventRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id"))))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<ZoneEvent> page = hasNext ? rows.subList(0, pageSize) : rows;
    List<AdminZoneEventResDto> items = page.stream().map(this::toDetail).toList();
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new AdminZoneEventPageResDto(items, nextCursor, hasNext);
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

    if (event.getStatus() == ZoneEventStatus.ACTIVE && request.touchesScheduledOnlyFields()) {
      throw new ConflictException("error.zone_event.invalid_state");
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
          .findByEvent_Id(eventId)
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
    return toDetail(event);
  }

  @Transactional
  public AdminZoneEventResDto activate(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    event.activate();
    return toDetail(event);
  }

  @Transactional
  public AdminZoneEventResDto close(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    event.close();
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
    return toDetail(event);
  }

  private AdminZoneEventResDto toDetail(ZoneEvent event) {
    ZoneEventAuthTarget target = authTargetRepository.findByEvent_Id(event.getId()).orElse(null);
    long joined = participationRepository.countByEvent_Id(event.getId());
    long success =
        participationRepository.countByEvent_IdAndStatus(
            event.getId(), ParticipationStatus.SUCCESS);
    return AdminZoneEventResDto.of(event, target, joined, success);
  }

  private Specification<ZoneEvent> buildListSpec(
      String zoneId,
      ZoneEventStatus status,
      OffsetDateTime from,
      OffsetDateTime to,
      Cursor cursor) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
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
      if (cursor != null) {
        Predicate earlier = cb.lessThan(root.get("startsAt"), cursor.startsAt());
        Predicate sameTimeLowerId =
            cb.and(
                cb.equal(root.get("startsAt"), cursor.startsAt()),
                cb.lessThan(root.get("id"), cursor.id()));
        predicates.add(cb.or(earlier, sameTimeLowerId));
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

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String encodeCursor(ZoneEvent event) {
    String raw = event.getStartsAt() + "|" + event.getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid admin event cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid admin event cursor.");
    }
  }

  private record Cursor(OffsetDateTime startsAt, UUID id) {}
}
