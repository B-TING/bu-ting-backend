package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.dto.request.ReportDismissReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdAction;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDecisionResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportListItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPayoutResDto;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 운영자 신고 검수: 목록·상세 조회, 인정·기각 처리. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventReportService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;
  private static final String UPHOLD_ENDPOINT = "zone-event-report-uphold";
  private static final String DISMISS_ENDPOINT = "zone-event-report-dismiss";

  private final ZoneEventReportRepository reportRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final OperatorAuthorization operatorAuthorization;
  private final RewardPayoutRepository rewardPayoutRepository;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  /** 신고 목록. status/roundId/eventId/participationId로 필터링한다. */
  @Transactional(readOnly = true)
  public AdminZoneEventReportPageResDto list(
      AuthenticatedUser user,
      String status,
      UUID roundId,
      UUID eventId,
      UUID participationId,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    ReportStatus statusFilter =
        status == null || status.isBlank()
            ? null
            : ReportStatus.valueOf(status.toUpperCase(Locale.ROOT));

    Page<ZoneEventReport> result =
        reportRepository.searchForAdmin(
            statusFilter,
            eventId,
            roundId,
            participationId,
            PageRequest.of(pageNumber - 1, pageSize));

    Map<UUID, ZoneEventParticipation> participations =
        participationRepository
            .findAllById(
                result.getContent().stream()
                    .map(ZoneEventReport::getParticipationId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(ZoneEventParticipation::getId, Function.identity()));
    List<AdminZoneEventReportListItemResDto> items =
        result.getContent().stream()
            .map(
                r ->
                    AdminZoneEventReportListItemResDto.of(
                        r, participations.get(r.getParticipationId())))
            .toList();
    return new AdminZoneEventReportPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  /** 신고 상세: 신고 내용 + 검수 대상 참여/이벤트 정보 + 관련 지급 건. */
  @Transactional(readOnly = true)
  public AdminZoneEventReportDetailResDto detail(AuthenticatedUser user, UUID reportId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventReport report =
        reportRepository
            .findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.report.not_found"));
    ZoneEventParticipation participation =
        participationRepository
            .findById(report.getParticipationId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));

    List<AdminZoneEventReportPayoutResDto> payouts = new ArrayList<>();
    rewardPayoutRepository
        .findByParticipationId(report.getParticipationId())
        .ifPresent(
            p ->
                payouts.add(
                    new AdminZoneEventReportPayoutResDto(
                        p.getId().toString(),
                        "TOP_LIKE",
                        p.getStatus().name(),
                        p.getHoldStatus().name())));
    baseRewardPayoutRepository
        .findByParticipationId(report.getParticipationId())
        .ifPresent(
            p ->
                payouts.add(
                    new AdminZoneEventReportPayoutResDto(
                        p.getId().toString(),
                        "BASE",
                        p.getStatus().name(),
                        p.getHoldStatus().name())));

    return new AdminZoneEventReportDetailResDto(
        report.getId().toString(),
        report.getParticipationId().toString(),
        participation.getEvent().getId().toString(),
        participation.getEvent().getRoundId() == null
            ? null
            : participation.getEvent().getRoundId().toString(),
        participation.getEvent().getZoneId(),
        report.getReporterId().toString(),
        report.getReasonCode().name(),
        report.getMemo(),
        report.getStatus().name(),
        report.getReviewedBy() == null ? null : report.getReviewedBy().toString(),
        report.getReviewedAt(),
        report.getDecisionNote(),
        report.getRevision(),
        report.getCreatedAt(),
        payouts);
  }

  /** 신고 인정. action=HOLD만 지원한다(DISQUALIFY는 별도 정책 확정 전까지 400). 관련 미지급 보상을 보류한다. */
  @Transactional
  public AdminZoneEventReportDecisionResDto uphold(
      AuthenticatedUser user, UUID reportId, ReportUpholdReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    if (request.action() != ReportUpholdAction.HOLD) {
      throw new IllegalArgumentException("error.zone_event.report.action_not_supported");
    }
    String fingerprint =
        reportId + ":" + request.note() + ":" + request.action() + ":" + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, UPHOLD_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminZoneEventReportDecisionResDto.class);
    }

    ZoneEventReport report = requireDecidable(reportId, request.expectedRevision());
    report.stampDecision(user.id(), request.note());
    report.resolveAs(ReportStatus.UPHELD);
    flushReport(report);

    rewardPayoutRepository
        .findByParticipationId(report.getParticipationId())
        .filter(payout -> payout.getHoldStatus() != PayoutHoldStatus.HELD_REPORT)
        .ifPresent(RewardPayout::hold);
    baseRewardPayoutRepository
        .findByParticipationId(report.getParticipationId())
        .filter(payout -> payout.getHoldStatus() != PayoutHoldStatus.HELD_REPORT)
        .ifPresent(BaseRewardPayout::hold);

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("UPHOLD_REPORT")
            .targetType("REPORT")
            .targetId(reportId)
            .detail(Map.of("note", request.note(), "action", request.action().name()))
            .build());

    AdminZoneEventReportDecisionResDto result =
        new AdminZoneEventReportDecisionResDto(
            report.getId().toString(),
            report.getStatus().name(),
            report.getParticipationId().toString(),
            report.getRevision());
    idempotencyService.save(idempotencyKey, UPHOLD_ENDPOINT, fingerprint, result);
    return result;
  }

  /**
   * 신고 기각. 다른 미해결 신고가 있으면 그 신고는 그대로 두고, 지급 보류도 절대 자동 해제하지 않는다 — 보류 해제는 {@code
   * /admin/reward-payouts/{payoutId}/release-hold}로만 가능하다(issue #243).
   */
  @Transactional
  public AdminZoneEventReportDecisionResDto dismiss(
      AuthenticatedUser user, UUID reportId, ReportDismissReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint = reportId + ":" + request.note() + ":" + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, DISMISS_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminZoneEventReportDecisionResDto.class);
    }

    ZoneEventReport report = requireDecidable(reportId, request.expectedRevision());
    report.stampDecision(user.id(), request.note());
    report.resolveAs(ReportStatus.DISMISSED);
    flushReport(report);

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("DISMISS_REPORT")
            .targetType("REPORT")
            .targetId(reportId)
            .detail(Map.of("note", request.note()))
            .build());

    AdminZoneEventReportDecisionResDto result =
        new AdminZoneEventReportDecisionResDto(
            report.getId().toString(),
            report.getStatus().name(),
            report.getParticipationId().toString(),
            report.getRevision());
    idempotencyService.save(idempotencyKey, DISMISS_ENDPOINT, fingerprint, result);
    return result;
  }

  private ZoneEventReport requireDecidable(UUID reportId, Long expectedRevision) {
    ZoneEventReport report =
        reportRepository
            .findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.report.not_found"));
    if (report.getStatus() != ReportStatus.OPEN && report.getStatus() != ReportStatus.REVIEWING) {
      throw new ConflictException("error.zone_event.report.invalid_state");
    }
    if (!report.getRevision().equals(expectedRevision)) {
      throw new ConflictException("error.zone_event.report.stale_revision");
    }
    return report;
  }

  private void flushReport(ZoneEventReport report) {
    try {
      reportRepository.saveAndFlush(report);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.zone_event.report.stale_revision");
    }
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("Failed to deserialize idempotent response.", e);
    }
  }
}
