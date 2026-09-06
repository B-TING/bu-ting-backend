package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.CommentPageResDto;
import com.butingbe.domain.zoneevent.dto.response.CommentResDto;
import com.butingbe.domain.zoneevent.dto.response.LikeResDto;
import com.butingbe.domain.zoneevent.dto.response.ReportResDto;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.ZoneEventComment;
import com.butingbe.domain.zoneevent.entity.ZoneEventLike;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventCommentRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventLikeRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
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
 * 공개 참여에 대한 좋아요·댓글·신고.
 *
 * <p>모든 상호작용은 공개·미숨김·성공 참여에만 허용된다(BR-08). 좋아요·댓글 수는 참여 행의 비정규화 카운터로 유지하고, 신고가 임계치만큼 쌓이면 참여를 자동
 * 숨김한다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventSocialService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventLikeRepository likeRepository;
  private final ZoneEventCommentRepository commentRepository;
  private final ZoneEventReportRepository reportRepository;
  private final UserRepository userRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Value("${zone-event.report.auto-hide-threshold:3}")
  private long autoHideThreshold;

  @Transactional
  public LikeResDto like(AuthenticatedUser user, UUID participationId) {
    UUID userId = requireUserId(user);
    ZoneEventParticipation participation = requireInteractable(participationId);
    if (participation.getUserId().equals(userId)) {
      throw new IllegalArgumentException("error.zone_event.like.self");
    }
    if (likeRepository.existsByParticipationIdAndUserId(participationId, userId)) {
      throw new ConflictException("error.zone_event.like.duplicate");
    }
    ZoneEventLike like =
        likeRepository.save(
            ZoneEventLike.builder().participationId(participationId).userId(userId).build());
    participation.increaseLikeCount();
    return LikeResDto.of(like, participation.getLikeCount());
  }

  @Transactional
  public void unlike(AuthenticatedUser user, UUID participationId) {
    UUID userId = requireUserId(user);
    ZoneEventLike like =
        likeRepository
            .findByParticipationIdAndUserId(participationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.like.duplicate"));
    likeRepository.delete(like);
    participationRepository
        .findById(participationId)
        .ifPresent(ZoneEventParticipation::decreaseLikeCount);
  }

  @Transactional
  public CommentResDto addComment(AuthenticatedUser user, UUID participationId, String content) {
    UUID userId = requireUserId(user);
    ZoneEventParticipation participation = requireInteractable(participationId);
    ZoneEventComment comment =
        commentRepository.save(
            ZoneEventComment.builder()
                .participationId(participationId)
                .userId(userId)
                .content(content)
                .build());
    participation.increaseCommentCount();
    return CommentResDto.of(comment, userRepository.findById(userId).orElse(null));
  }

  @Transactional(readOnly = true)
  public CommentPageResDto getComments(UUID participationId, String cursor, Integer size) {
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);
    Specification<ZoneEventComment> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("participationId"), participationId));
          predicates.add(cb.isNull(root.get("deletedAt")));
          if (decoded != null) {
            Predicate later = cb.greaterThan(root.get("createdAt"), decoded.createdAt());
            Predicate sameTimeHigherId =
                cb.and(
                    cb.equal(root.get("createdAt"), decoded.createdAt()),
                    cb.greaterThan(root.get("id"), decoded.id()));
            predicates.add(cb.or(later, sameTimeHigherId));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    List<ZoneEventComment> rows =
        commentRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))))
            .getContent();
    boolean hasNext = rows.size() > pageSize;
    List<ZoneEventComment> page = hasNext ? rows.subList(0, pageSize) : rows;
    Map<UUID, User> authors = authorsOf(page);
    List<CommentResDto> items =
        page.stream().map(c -> CommentResDto.of(c, authors.get(c.getUserId()))).toList();
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new CommentPageResDto(items, nextCursor, hasNext);
  }

  @Transactional
  public CommentResDto editComment(AuthenticatedUser user, UUID commentId, String content) {
    UUID userId = requireUserId(user);
    ZoneEventComment comment = requireComment(commentId);
    requireAuthorOrOperator(user, userId, comment.getUserId());
    comment.edit(content);
    return CommentResDto.of(comment, userRepository.findById(comment.getUserId()).orElse(null));
  }

  @Transactional
  public void deleteComment(AuthenticatedUser user, UUID commentId) {
    UUID userId = requireUserId(user);
    ZoneEventComment comment = requireComment(commentId);
    requireAuthorOrOperator(user, userId, comment.getUserId());
    comment.softDelete();
    participationRepository
        .findById(comment.getParticipationId())
        .ifPresent(ZoneEventParticipation::decreaseCommentCount);
  }

  @Transactional
  public ReportResDto report(
      AuthenticatedUser user, UUID participationId, String reasonCode, String memo) {
    UUID userId = requireUserId(user);
    ZoneEventParticipation participation = requireInteractable(participationId);
    if (participation.getUserId().equals(userId)) {
      throw new IllegalArgumentException("error.zone_event.report.self");
    }
    if (reportRepository.existsByParticipationIdAndReporterId(participationId, userId)) {
      throw new DuplicateResourceException("error.zone_event.report.duplicate");
    }
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(participationId)
                .reporterId(userId)
                .reasonCode(parseReason(reasonCode))
                .memo(memo)
                .build());
    if (reportRepository.countByParticipationId(participationId) >= autoHideThreshold) {
      participation.hide();
    }
    return ReportResDto.from(report);
  }

  private ZoneEventParticipation requireInteractable(UUID participationId) {
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (!participation.isInteractable()) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    return participation;
  }

  private ZoneEventComment requireComment(UUID commentId) {
    ZoneEventComment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.comment.not_found"));
    if (comment.isDeleted()) {
      throw new ResourceNotFoundException("error.zone_event.comment.not_found");
    }
    return comment;
  }

  private void requireAuthorOrOperator(AuthenticatedUser user, UUID userId, UUID authorId) {
    if (!userId.equals(authorId) && !operatorAuthorization.isOperator(user)) {
      throw new ForbiddenException("error.zone_event.participation.forbidden");
    }
  }

  private Map<UUID, User> authorsOf(List<ZoneEventComment> page) {
    List<UUID> ids = page.stream().map(ZoneEventComment::getUserId).distinct().toList();
    return userRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }

  private ReportReasonCode parseReason(String reasonCode) {
    try {
      return ReportReasonCode.valueOf(reasonCode.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.participation.invalid_state");
    }
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String encodeCursor(ZoneEventComment comment) {
    String raw = comment.getCreatedAt() + "|" + comment.getId();
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
        throw new IllegalArgumentException("Invalid comment cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid comment cursor.");
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
