package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportListItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPayoutResDto;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 신고 검수: 목록·상세 조회, 인정·기각 처리. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventReportService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventReportRepository reportRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final OperatorAuthorization operatorAuthorization;
  private final RewardPayoutRepository rewardPayoutRepository;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;

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
}
