package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueueItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminSubmissionDetailResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
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

/** 사진 인증 검수: 큐 조회, 상세 조회, 제출 단위 승인/반려. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventReviewService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final UserRepository userRepository;
  private final FileStorageService fileStorageService;

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

  /** 검수 상세: 사용자 표시 정보 + 현재 제출 + 전체 제출 이력(attemptNo 내림차순) + 당시 타겟/보상 스냅샷. */
  @Transactional(readOnly = true)
  public AdminReviewDetailResDto detail(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    User participant =
        userRepository
            .findById(participation.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found"));

    List<ZoneEventSubmission> history =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(participationId);
    List<AdminSubmissionDetailResDto> historyDtos =
        history.stream()
            .map(s -> AdminSubmissionDetailResDto.of(s, presignedUrl(s.getMediaFileKey())))
            .toList();
    AdminSubmissionDetailResDto current =
        historyDtos.stream()
            .filter(
                s ->
                    participation.getCurrentSubmissionId() != null
                        && s.submissionId().equals(participation.getCurrentSubmissionId().toString()))
            .findFirst()
            .orElse(historyDtos.isEmpty() ? null : historyDtos.get(0));

    return AdminReviewDetailResDto.of(
        participation, participant.getNickname(), participant.getEmail(), current, historyDtos);
  }

  private String presignedUrl(String mediaFileKey) {
    return mediaFileKey == null ? null : fileStorageService.getPresignedUrl(mediaFileKey);
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
