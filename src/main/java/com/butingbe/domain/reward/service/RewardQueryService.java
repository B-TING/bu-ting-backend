package com.butingbe.domain.reward.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.reward.dto.response.PointLedgerItemResDto;
import com.butingbe.domain.reward.dto.response.PointLedgerPageResDto;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto.BadgeGroup;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto.BadgeItem;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.UserBadge;
import com.butingbe.domain.reward.entity.UserPointLedger;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserBadgeRepository;
import com.butingbe.domain.reward.repository.UserPointLedgerRepository;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 보상 조회.
 *
 * <p>배지는 구역별로 묶어 내려준다. 배지가 어느 구역 것인지는 지급 기록(grant)의 event로 해석하므로, 이 조회는 zoneevent 리포지토리를 단방향으로 읽는다.
 * 지급 로직({@link RewardService})의 무결합 원칙과는 별개다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardQueryService {

  private static final String UNKNOWN_ZONE = "UNKNOWN";
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final UserBadgeRepository userBadgeRepository;
  private final RewardGrantRepository rewardGrantRepository;
  private final UserPointLedgerRepository userPointLedgerRepository;
  private final ZoneEventRepository zoneEventRepository;
  private final UserPointService userPointService;
  private final FileStorageService fileStorageService;

  @Value("${file-storage.s3.presigned-url-expiration:3600}")
  private int presignedUrlExpiration;

  /** 포인트 잔액 + 구역별 배지. 쿠폰은 Phase 2부터. */
  public UserRewardsResDto myRewards(AuthenticatedUser user) {
    UUID userId = requireUserId(user);
    List<UserBadge> badges = userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
    Map<UUID, String> zoneByBadgeGrant = resolveZones(badges);

    Map<String, List<BadgeItem>> grouped = new LinkedHashMap<>();
    for (UserBadge badge : badges) {
      String zoneId = zoneByBadgeGrant.getOrDefault(badge.getGrantId(), UNKNOWN_ZONE);
      grouped
          .computeIfAbsent(zoneId, key -> new ArrayList<>())
          .add(
              new BadgeItem(
                  badge.getReward().getCode(),
                  badge.getReward().getName(),
                  presignedUrl(badge.getReward().getImageFileKey()),
                  badge.getEarnedAt()));
    }

    List<BadgeGroup> badgeGroups =
        grouped.entrySet().stream()
            .map(entry -> new BadgeGroup(entry.getKey(), entry.getValue()))
            .toList();
    return new UserRewardsResDto(userPointService.getBalance(userId), badgeGroups, List.of());
  }

  /** 포인트 원장. createdAt 내림차순 커서 페이징. */
  public PointLedgerPageResDto pointLedger(AuthenticatedUser user, String cursor, Integer size) {
    UUID userId = requireUserId(user);
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);

    Specification<UserPointLedger> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("userId"), userId));
          if (decoded != null) {
            Predicate earlier = cb.lessThan(root.get("createdAt"), decoded.createdAt());
            Predicate sameTimeLowerId =
                cb.and(
                    cb.equal(root.get("createdAt"), decoded.createdAt()),
                    cb.lessThan(root.get("id"), decoded.id()));
            predicates.add(cb.or(earlier, sameTimeLowerId));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    List<UserPointLedger> rows =
        userPointLedgerRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<UserPointLedger> page = hasNext ? rows.subList(0, pageSize) : rows;
    List<PointLedgerItemResDto> items = page.stream().map(PointLedgerItemResDto::from).toList();
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new PointLedgerPageResDto(items, nextCursor, hasNext);
  }

  /** 배지 지급 grant의 event로부터 grantId → zoneId 매핑을 만든다. */
  private Map<UUID, String> resolveZones(List<UserBadge> badges) {
    if (badges.isEmpty()) {
      return Map.of();
    }
    List<UUID> grantIds = badges.stream().map(UserBadge::getGrantId).toList();
    Map<UUID, RewardGrant> grants =
        rewardGrantRepository.findAllById(grantIds).stream()
            .collect(Collectors.toMap(RewardGrant::getId, Function.identity()));
    List<UUID> eventIds =
        grants.values().stream().map(RewardGrant::getEventId).filter(id -> id != null).toList();
    Map<UUID, String> zoneByEvent =
        zoneEventRepository.findAllById(eventIds).stream()
            .collect(Collectors.toMap(ZoneEvent::getId, ZoneEvent::getZoneId));

    Map<UUID, String> zoneByGrant = new LinkedHashMap<>();
    for (RewardGrant grant : grants.values()) {
      String zoneId = grant.getEventId() == null ? null : zoneByEvent.get(grant.getEventId());
      zoneByGrant.put(grant.getId(), zoneId == null ? UNKNOWN_ZONE : zoneId);
    }
    return zoneByGrant;
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String presignedUrl(String fileKey) {
    return fileKey == null ? null : fileStorageService.getPresignedUrl(fileKey);
  }

  private String encodeCursor(UserPointLedger ledger) {
    String raw = ledger.getCreatedAt() + "|" + ledger.getId();
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
        throw new IllegalArgumentException("Invalid point ledger cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid point ledger cursor.");
    }
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }

  private record Cursor(OffsetDateTime createdAt, UUID id) {}
}
