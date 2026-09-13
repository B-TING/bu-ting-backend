package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.place.dto.response.PlaceSummaryResDto;
import com.butingbe.domain.place.service.PlaceService;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetReplaceReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 인증 타겟(선택 장소) 전용 운영 API. 이벤트 CRUD와 분리해, 좌표·반경·예시 이미지를 개별 타겟 단위로 관리한다. 모든 메서드는 ROLE_ADMIN/MANAGER만
 * 호출할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class AdminZoneEventTargetService {

  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final PlaceService placeService;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public List<AdminAuthTargetResDto> list(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    findEvent(eventId);
    return authTargetRepository.findByEvent_Id(eventId).stream()
        .map(AdminAuthTargetResDto::from)
        .toList();
  }

  @Transactional
  public AdminAuthTargetResDto create(
      AuthenticatedUser user, UUID eventId, AdminAuthTargetCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    ZoneEventTargetKind kind = parseKind(request.targetKind());

    String placeName;
    String placeContentId = null;
    String contentTypeId = null;
    Double sourceLatitude = null;
    Double sourceLongitude = null;

    if (kind == ZoneEventTargetKind.PLACE) {
      if (!StringUtils.hasText(request.placeContentId())
          || !StringUtils.hasText(request.contentTypeId())) {
        throw new IllegalArgumentException("error.zone_event.target.invalid_kind");
      }
      placeContentId = request.placeContentId();
      contentTypeId = request.contentTypeId();
      requireNoDuplicate(eventId, placeContentId, null);

      PlaceSummaryResDto summary = placeService.getPlaceSummary(placeContentId);
      if (summary == null || summary.latitude() == null || summary.longitude() == null) {
        throw new IllegalArgumentException("error.zone_event.place_not_found");
      }
      sourceLatitude = summary.latitude();
      sourceLongitude = summary.longitude();
      placeName = StringUtils.hasText(request.placeName()) ? request.placeName() : summary.title();
    } else {
      if (!StringUtils.hasText(request.landmarkId()) || !StringUtils.hasText(request.placeName())) {
        throw new IllegalArgumentException("error.zone_event.target.invalid_kind");
      }
      placeName = request.placeName();
    }

    double[] coordinates =
        resolveCoordinates(
            request.latitude(), request.longitude(), sourceLatitude, sourceLongitude);

    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(kind)
                .landmarkId(request.landmarkId())
                .placeContentId(placeContentId)
                .contentTypeId(contentTypeId)
                .placeName(placeName)
                .guideText(request.guideText())
                .exampleFileKey(request.exampleFileKey())
                .sourceLatitude(sourceLatitude)
                .sourceLongitude(sourceLongitude)
                .latitude(coordinates[0])
                .longitude(coordinates[1])
                .radiusM(request.radiusM())
                .build());

    audit(user, "CREATE_TARGET", target.getId(), Map.of("eventId", eventId.toString()));
    return AdminAuthTargetResDto.from(target);
  }

  @Transactional
  public AdminAuthTargetResDto patch(
      AuthenticatedUser user, UUID eventId, UUID targetId, AdminAuthTargetPatchReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventAuthTarget target = requireTarget(eventId, targetId);
    if (target.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (!target.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if ((request.latitude() == null) != (request.longitude() == null)) {
      throw new IllegalArgumentException("error.zone_event.target.invalid_coordinates");
    }

    Map<String, Object> before = snapshot(target);
    target.update(
        null,
        request.guideText(),
        request.exampleFileKey(),
        request.latitude(),
        request.longitude(),
        request.radiusM());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("eventId", eventId.toString());
    if (request.reason() != null) {
      detail.put("reason", request.reason());
    }
    detail.put("before", before);
    detail.put("after", snapshot(target));
    audit(user, "PATCH_TARGET", target.getId(), detail);
    return AdminAuthTargetResDto.from(target);
  }

  @Transactional
  public AdminAuthTargetResDto replace(
      AuthenticatedUser user, UUID eventId, UUID targetId, AdminAuthTargetReplaceReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event = findEvent(eventId);
    ZoneEventAuthTarget old = requireTarget(eventId, targetId);
    if (old.getStatus() != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    requireNoDuplicate(eventId, request.placeContentId(), old.getId());

    PlaceSummaryResDto summary = placeService.getPlaceSummary(request.placeContentId());
    if (summary == null || summary.latitude() == null || summary.longitude() == null) {
      throw new IllegalArgumentException("error.zone_event.place_not_found");
    }
    double[] coordinates =
        resolveCoordinates(
            request.latitude(), request.longitude(), summary.latitude(), summary.longitude());

    old.markReplaced();

    ZoneEventAuthTarget replacement =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeContentId(request.placeContentId())
                .contentTypeId(request.contentTypeId())
                .placeName(summary.title())
                .guideText(request.guideText())
                .exampleFileKey(request.exampleFileKey())
                .sourceLatitude(summary.latitude())
                .sourceLongitude(summary.longitude())
                .latitude(coordinates[0])
                .longitude(coordinates[1])
                .radiusM(request.radiusM())
                .build());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("eventId", eventId.toString());
    detail.put("oldTargetId", old.getId().toString());
    if (request.reason() != null) {
      detail.put("reason", request.reason());
    }
    audit(user, "REPLACE_TARGET", replacement.getId(), detail);
    return AdminAuthTargetResDto.from(replacement);
  }

  @Transactional
  public AdminAuthTargetResDto cancel(AuthenticatedUser user, UUID eventId, UUID targetId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventAuthTarget target = requireTarget(eventId, targetId);
    target.cancel();
    audit(user, "CANCEL_TARGET", target.getId(), Map.of("eventId", eventId.toString()));
    return AdminAuthTargetResDto.from(target);
  }

  private void requireNoDuplicate(UUID eventId, String placeContentId, UUID excludeTargetId) {
    authTargetRepository
        .findByEvent_IdAndPlaceContentIdAndStatus(
            eventId, placeContentId, ZoneEventTargetStatus.ACTIVE)
        .filter(existing -> excludeTargetId == null || !existing.getId().equals(excludeTargetId))
        .ifPresent(
            existing -> {
              throw new DuplicateResourceException("error.zone_event.target.duplicate");
            });
  }

  private double[] resolveCoordinates(
      Double requestedLatitude,
      Double requestedLongitude,
      Double sourceLatitude,
      Double sourceLongitude) {
    if (requestedLatitude == null && requestedLongitude == null) {
      if (sourceLatitude == null || sourceLongitude == null) {
        throw new IllegalArgumentException("error.zone_event.target.invalid_coordinates");
      }
      return new double[] {sourceLatitude, sourceLongitude};
    }
    if (requestedLatitude == null || requestedLongitude == null) {
      throw new IllegalArgumentException("error.zone_event.target.invalid_coordinates");
    }
    return new double[] {requestedLatitude, requestedLongitude};
  }

  private ZoneEventTargetKind parseKind(String kind) {
    try {
      return ZoneEventTargetKind.valueOf(kind.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.target.invalid_kind");
    }
  }

  private ZoneEvent findEvent(UUID eventId) {
    return zoneEventRepository
        .findById(eventId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private ZoneEventAuthTarget requireTarget(UUID eventId, UUID targetId) {
    return authTargetRepository
        .findByIdAndEvent_Id(targetId, eventId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.target_not_found"));
  }

  private Map<String, Object> snapshot(ZoneEventAuthTarget target) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("latitude", target.getLatitude());
    map.put("longitude", target.getLongitude());
    map.put("radiusM", target.getRadiusM());
    map.put("guideText", target.getGuideText());
    map.put("exampleFileKey", target.getExampleFileKey());
    return map;
  }

  private void audit(
      AuthenticatedUser user, String action, UUID targetId, Map<String, Object> detail) {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(action)
            .targetType("TARGET")
            .targetId(targetId)
            .detail(detail)
            .build());
  }
}
