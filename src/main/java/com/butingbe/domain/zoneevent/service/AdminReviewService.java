package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.reward.service.RewardRevokeService;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminParticipationListItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminParticipationPageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 검수: 성공 참여의 회수·신고 자동 숨김 해제. 검수 큐/승인/반려는 {@link AdminZoneEventReviewService}로 이동했다. */
@Service
@RequiredArgsConstructor
public class AdminReviewService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventReportRepository reportRepository;
  private final RewardRevokeService rewardRevokeService;
  private final OperatorAuthorization operatorAuthorization;
  private final UserRepository userRepository;

  /** SUCCESS → REVOKED + 보상 회수(포인트 되돌림, 미사용 쿠폰 회수). */
  @Transactional
  public void revoke(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.SUCCESS);
    participation.stampReview(user.id());
    participation.markRevoked();
    rewardRevokeService.revokeParticipationRewards(participationId);
  }

  /** 신고 자동 숨김 해제 + 신고 DISMISSED. */
  @Transactional
  public void unhide(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    participation.unhide();
    for (ZoneEventReport report : reportRepository.findByParticipationId(participationId)) {
      report.resolveAs(ReportStatus.DISMISSED);
    }
  }

  /** 전체 참여 목록. roundId/eventId/zoneId/userId/status/keyword(닉네임·이메일)로 필터링한다. */
  @Transactional(readOnly = true)
  public AdminParticipationPageResDto list(
      AuthenticatedUser user,
      UUID roundId,
      UUID eventId,
      String zoneId,
      UUID userId,
      String status,
      String keyword,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    ParticipationStatus statusFilter =
        status == null || status.isBlank() ? null : ParticipationStatus.valueOf(status.toUpperCase());
    String resolvedZoneId =
        zoneId == null || zoneId.isBlank() ? null : ChatZone.fromString(zoneId).name();

    List<UUID> keywordUserIds = null;
    if (keyword != null && !keyword.isBlank()) {
      keywordUserIds =
          userRepository
              .findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword)
              .stream()
              .map(u -> u.getId())
              .toList();
      if (keywordUserIds.isEmpty()) {
        return new AdminParticipationPageResDto(List.of(), pageNumber, pageSize, 0, 0, false);
      }
    }

    Specification<ZoneEventParticipation> spec =
        buildListSpec(roundId, eventId, resolvedZoneId, userId, statusFilter, keywordUserIds);
    Page<ZoneEventParticipation> result =
        participationRepository.findAll(
            spec, PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.desc("joinedAt"))));

    List<AdminParticipationListItemResDto> items =
        result.getContent().stream().map(AdminParticipationListItemResDto::of).toList();
    return new AdminParticipationPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  private Specification<ZoneEventParticipation> buildListSpec(
      UUID roundId,
      UUID eventId,
      String zoneId,
      UUID userId,
      ParticipationStatus status,
      List<UUID> keywordUserIds) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (roundId != null) {
        predicates.add(cb.equal(root.get("event").get("roundId"), roundId));
      }
      if (eventId != null) {
        predicates.add(cb.equal(root.get("event").get("id"), eventId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      if (userId != null) {
        predicates.add(cb.equal(root.get("userId"), userId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (keywordUserIds != null) {
        predicates.add(root.get("userId").in(keywordUserIds));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private ZoneEventParticipation requireStatus(UUID participationId, ParticipationStatus expected) {
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (participation.getStatus() != expected) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    return participation;
  }
}
