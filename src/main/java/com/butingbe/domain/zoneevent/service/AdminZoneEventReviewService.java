package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueueItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
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

/** 사진 인증 검수: 큐 조회, 상세 조회, 제출 단위 승인/반려. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventReviewService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final OperatorAuthorization operatorAuthorization;

  /** 검수 큐: UNDER_REVIEW 참여만, roundId/eventId/zoneId로 필터링, joinedAt 오름차순(먼저 온 순). */
  @Transactional(readOnly = true)
  public AdminReviewQueuePageResDto queue(
      AuthenticatedUser user, UUID roundId, UUID eventId, String zoneId, Integer page, Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String resolvedZoneId = zoneId == null || zoneId.isBlank() ? null : ChatZone.fromString(zoneId).name();

    Specification<ZoneEventParticipation> spec = buildQueueSpec(roundId, eventId, resolvedZoneId);
    Page<ZoneEventParticipation> result =
        participationRepository.findAll(
            spec, PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.asc("joinedAt"))));

    List<AdminReviewQueueItemResDto> items =
        result.getContent().stream().map(AdminReviewQueueItemResDto::of).toList();
    return new AdminReviewQueuePageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  private Specification<ZoneEventParticipation> buildQueueSpec(UUID roundId, UUID eventId, String zoneId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("status"), ParticipationStatus.UNDER_REVIEW));
      if (roundId != null) {
        predicates.add(cb.equal(root.get("event").get("roundId"), roundId));
      }
      if (eventId != null) {
        predicates.add(cb.equal(root.get("event").get("id"), eventId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
