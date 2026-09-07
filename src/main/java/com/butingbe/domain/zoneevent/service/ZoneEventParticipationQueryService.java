package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryItemResDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryPageResDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 참여 조회: 이벤트별 내 참여 목록과, 필터·커서 페이징 기반 전체 이력. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneEventParticipationQueryService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final RewardGrantRepository rewardGrantRepository;
  private final FileStorageService fileStorageService;

  @Value("${file-storage.s3.presigned-url-expiration:3600}")
  private int presignedUrlExpiration;

  /** 이 이벤트에 대한 내 참여 목록(최신순, 취소 포함). */
  public List<ParticipationResDto> myEventParticipations(AuthenticatedUser user, UUID eventId) {
    UUID userId = requireUserId(user);
    return participationRepository
        .findByEvent_IdAndUserIdOrderByJoinedAtDesc(eventId, userId)
        .stream()
        .map(participation -> ParticipationResDto.of(participation, null))
        .toList();
  }

  /** 내 참여 이력. joinedAt 내림차순 커서 페이징 + 구역·타입·상태·기간 필터. */
  public ParticipationHistoryPageResDto history(
      AuthenticatedUser user,
      String zone,
      String type,
      List<ParticipationStatus> statuses,
      OffsetDateTime from,
      OffsetDateTime to,
      String cursor,
      Integer size) {
    UUID userId = requireUserId(user);
    String zoneId = zone == null || zone.isBlank() ? null : ChatZone.fromString(zone).name();
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);

    Specification<ZoneEventParticipation> spec =
        buildSpec(userId, zoneId, type, statuses, from, to, decoded);
    List<ZoneEventParticipation> rows =
        participationRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.desc("joinedAt"), Sort.Order.desc("id"))))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<ZoneEventParticipation> page = hasNext ? rows.subList(0, pageSize) : rows;

    Map<UUID, List<GrantedRewardDto>> rewardsByParticipation = rewardsFor(page);
    List<ParticipationHistoryItemResDto> items =
        page.stream()
            .map(
                participation ->
                    ParticipationHistoryItemResDto.of(
                        participation,
                        presignedUrl(participation.getMediaFileKey()),
                        presignedUrlExpiration,
                        rewardsByParticipation.getOrDefault(participation.getId(), List.of())))
            .toList();

    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new ParticipationHistoryPageResDto(items, nextCursor, hasNext);
  }

  private Map<UUID, List<GrantedRewardDto>> rewardsFor(List<ZoneEventParticipation> page) {
    if (page.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = page.stream().map(ZoneEventParticipation::getId).toList();
    return rewardGrantRepository.findByParticipationIdInAndRevokedAtIsNull(ids).stream()
        .collect(
            Collectors.groupingBy(
                RewardGrant::getParticipationId,
                Collectors.mapping(
                    grant -> GrantedRewardDto.of(grant, grant.getReward().getPointAmount()),
                    Collectors.toList())));
  }

  private Specification<ZoneEventParticipation> buildSpec(
      UUID userId,
      String zoneId,
      String type,
      List<ParticipationStatus> statuses,
      OffsetDateTime from,
      OffsetDateTime to,
      Cursor cursor) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userId"), userId));
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      if (type != null && !type.isBlank()) {
        predicates.add(cb.equal(root.get("event").get("type").get("typeCode"), type));
      }
      if (statuses != null && !statuses.isEmpty()) {
        predicates.add(root.get("status").in(statuses));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("joinedAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("joinedAt"), to));
      }
      if (cursor != null) {
        Predicate earlier = cb.lessThan(root.get("joinedAt"), cursor.joinedAt());
        Predicate sameTimeLowerId =
            cb.and(
                cb.equal(root.get("joinedAt"), cursor.joinedAt()),
                cb.lessThan(root.get("id"), cursor.id()));
        predicates.add(cb.or(earlier, sameTimeLowerId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String presignedUrl(String mediaFileKey) {
    return mediaFileKey == null ? null : fileStorageService.getPresignedUrl(mediaFileKey);
  }

  private String encodeCursor(ZoneEventParticipation participation) {
    String raw = participation.getJoinedAt() + "|" + participation.getId();
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
        throw new IllegalArgumentException("Invalid participation cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid participation cursor.");
    }
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }

  private record Cursor(OffsetDateTime joinedAt, UUID id) {}
}
