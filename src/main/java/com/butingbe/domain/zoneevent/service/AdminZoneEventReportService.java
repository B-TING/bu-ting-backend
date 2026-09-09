package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportListItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPageResDto;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
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
                result.getContent().stream().map(ZoneEventReport::getParticipationId).distinct().toList())
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
}
