package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AlbumItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AlbumPageResDto;
import com.butingbe.domain.zoneevent.entity.AlbumSort;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ZoneEventLike;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventLikeRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 공개 앨범 조회와 참여 공개 설정.
 *
 * <p>앨범은 공개(PUBLIC)이고 숨김이 아닌 성공 참여만 보여준다. 이벤트·구역·회차 범위별로 같은 형태로 조회하며, 정렬은 최신순/좋아요순이다. 개인화 필드
 * (likedByMe, isMine)는 로그인 시에만 채운다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventAlbumService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventLikeRepository likeRepository;
  private final UserRepository userRepository;
  private final FileStorageService fileStorageService;

  @Value("${file-storage.s3.presigned-url-expiration:3600}")
  private int presignedUrlExpiration;

  @Transactional(readOnly = true)
  public AlbumPageResDto eventAlbum(
      UUID eventId, String sort, String cursor, Integer size, UUID viewerId) {
    return album(
        (root, query, cb) -> cb.equal(root.get("event").get("id"), eventId),
        sort,
        cursor,
        size,
        viewerId);
  }

  @Transactional(readOnly = true)
  public AlbumPageResDto zoneAlbum(
      String zone, String sort, String cursor, Integer size, UUID viewerId) {
    String zoneId = parseZone(zone);
    return album(
        (root, query, cb) -> cb.equal(root.get("event").get("zoneId"), zoneId),
        sort,
        cursor,
        size,
        viewerId);
  }

  @Transactional(readOnly = true)
  public AlbumPageResDto roundAlbum(
      UUID roundId, String sort, String cursor, Integer size, UUID viewerId) {
    return album(
        (root, query, cb) -> cb.equal(root.get("event").get("roundId"), roundId),
        sort,
        cursor,
        size,
        viewerId);
  }

  /** 참여 공개 범위를 바꾼다. 본인·SUCCESS 참여만 가능. */
  @Transactional
  public void setVisibility(AuthenticatedUser user, UUID participationId, String visibility) {
    UUID userId = requireUserId(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (!participation.getUserId().equals(userId)) {
      throw new ForbiddenException("error.zone_event.participation.forbidden");
    }
    if (participation.getStatus() != ParticipationStatus.SUCCESS) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    participation.changeVisibility(parseVisibility(visibility));
  }

  private AlbumPageResDto album(
      Specification<ZoneEventParticipation> scope,
      String sortValue,
      String cursor,
      Integer size,
      UUID viewerId) {
    AlbumSort sort = parseSort(sortValue);
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor, sort);

    Specification<ZoneEventParticipation> spec =
        scope.and(publicSuccess()).and(keyset(sort, decoded));
    List<ZoneEventParticipation> rows =
        participationRepository
            .findAll(spec, PageRequest.of(0, pageSize + 1, order(sort)))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<ZoneEventParticipation> page = hasNext ? rows.subList(0, pageSize) : rows;

    Map<UUID, User> authors = authorsOf(page);
    Set<UUID> likedByViewer = likedParticipationIds(page, viewerId);

    List<AlbumItemResDto> items =
        page.stream().map(p -> toItem(p, authors, likedByViewer, viewerId)).toList();
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1), sort) : null;
    return new AlbumPageResDto(items, nextCursor, hasNext);
  }

  private Specification<ZoneEventParticipation> publicSuccess() {
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("status"), ParticipationStatus.SUCCESS),
            cb.equal(root.get("visibility"), ParticipationVisibility.PUBLIC),
            cb.isFalse(root.get("hidden")));
  }

  private Specification<ZoneEventParticipation> keyset(AlbumSort sort, Cursor cursor) {
    return (root, query, cb) -> {
      if (cursor == null) {
        return cb.conjunction();
      }
      if (sort == AlbumSort.MOST_LIKED) {
        Predicate lowerLike = cb.lessThan(root.get("likeCount"), cursor.likeCount());
        Predicate sameLikeEarlier =
            cb.and(cb.equal(root.get("likeCount"), cursor.likeCount()), tieBreak(root, cb, cursor));
        return cb.or(lowerLike, sameLikeEarlier);
      }
      return tieBreak(root, cb, cursor);
    };
  }

  private Predicate tieBreak(
      jakarta.persistence.criteria.Root<ZoneEventParticipation> root,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      Cursor cursor) {
    Predicate earlier = cb.lessThan(root.get("completedAt"), cursor.completedAt());
    Predicate sameTimeLowerId =
        cb.and(
            cb.equal(root.get("completedAt"), cursor.completedAt()),
            cb.lessThan(root.get("id"), cursor.id()));
    return cb.or(earlier, sameTimeLowerId);
  }

  private Sort order(AlbumSort sort) {
    if (sort == AlbumSort.MOST_LIKED) {
      return Sort.by(
          Sort.Order.desc("likeCount"), Sort.Order.desc("completedAt"), Sort.Order.desc("id"));
    }
    return Sort.by(Sort.Order.desc("completedAt"), Sort.Order.desc("id"));
  }

  private Map<UUID, User> authorsOf(List<ZoneEventParticipation> page) {
    List<UUID> userIds = page.stream().map(ZoneEventParticipation::getUserId).distinct().toList();
    return userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }

  private Set<UUID> likedParticipationIds(List<ZoneEventParticipation> page, UUID viewerId) {
    if (viewerId == null || page.isEmpty()) {
      return Set.of();
    }
    List<UUID> ids = page.stream().map(ZoneEventParticipation::getId).toList();
    return likeRepository.findByUserIdAndParticipationIdIn(viewerId, ids).stream()
        .map(ZoneEventLike::getParticipationId)
        .collect(Collectors.toSet());
  }

  private AlbumItemResDto toItem(
      ZoneEventParticipation p, Map<UUID, User> authors, Set<UUID> liked, UUID viewerId) {
    User author = authors.get(p.getUserId());
    return new AlbumItemResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getTitle(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        author == null ? null : author.getNickname(),
        author == null ? null : author.getProfileImageUrl(),
        null,
        p.getContent(),
        p.getMediaFileKey() == null
            ? null
            : fileStorageService.getPresignedUrl(p.getMediaFileKey()),
        p.getMediaFileKey() == null ? null : presignedUrlExpiration,
        p.getLikeCount(),
        liked.contains(p.getId()),
        p.getCommentCount(),
        viewerId != null && viewerId.equals(p.getUserId()),
        p.getCompletedAt());
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
  }

  private AlbumSort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return AlbumSort.LATEST;
    }
    try {
      return AlbumSort.valueOf(sort.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_sort");
    }
  }

  private ParticipationVisibility parseVisibility(String visibility) {
    try {
      return ParticipationVisibility.valueOf(visibility.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.participation.invalid_state");
    }
  }

  private String encodeCursor(ZoneEventParticipation p, AlbumSort sort) {
    String raw =
        sort == AlbumSort.MOST_LIKED
            ? p.getLikeCount() + "|" + p.getCompletedAt() + "|" + p.getId()
            : p.getCompletedAt() + "|" + p.getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String cursor, AlbumSort sort) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      if (sort == AlbumSort.MOST_LIKED) {
        if (parts.length != 3) {
          throw new IllegalArgumentException("Invalid album cursor.");
        }
        return new Cursor(
            Long.parseLong(parts[0]), OffsetDateTime.parse(parts[1]), UUID.fromString(parts[2]));
      }
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid album cursor.");
      }
      return new Cursor(0L, OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid album cursor.");
    }
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }

  private record Cursor(long likeCount, OffsetDateTime completedAt, UUID id) {}
}
