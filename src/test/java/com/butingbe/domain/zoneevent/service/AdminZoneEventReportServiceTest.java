package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.ReportDismissReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdAction;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdReqDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventReportServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventReportService reportService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private RewardPayoutRepository rewardPayoutRepository;
  @Autowired private BaseRewardPayoutRepository baseRewardPayoutRepository;

  private ZoneEvent event;
  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    operator =
        new AuthenticatedUser(
            userRepository
                .save(
                    User.builder()
                        .email("op-" + UUID.randomUUID() + "@example.com")
                        .provider("google")
                        .providerId("google-" + UUID.randomUUID())
                        .name(new Name("Kim", "Tester"))
                        .nickname("op")
                        .role(UserRole.USER)
                        .build())
                .getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("신고 목록은 status·eventId·participationId로 필터링하고 페이지 정보를 돌려준다")
  void listFiltersAndPages() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    ZoneEventReport open =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p1.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p2.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.OTHER)
            .build());

    var page = reportService.list(operator, "OPEN", null, event.getId(), p1.getId(), 1, 20);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).reportId()).isEqualTo(open.getId().toString());
    assertThat(page.items().get(0).eventId()).isEqualTo(event.getId().toString());
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  @DisplayName("운영자가 아니면 목록 조회는 403이다")
  void listForbidden() {
    AuthenticatedUser normalUser =
        new AuthenticatedUser(
            userRepository
                .save(
                    User.builder()
                        .email("n-" + UUID.randomUUID() + "@example.com")
                        .provider("google")
                        .providerId("google-" + UUID.randomUUID())
                        .name(new Name("Kim", "Tester"))
                        .nickname("normal")
                        .role(UserRole.USER)
                        .build())
                .getId(),
            "n@example.com",
            "normal",
            List.of());
    assertThatThrownBy(() -> reportService.list(normalUser, null, null, null, null, 1, 20))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("신고 상세는 참여·이벤트·관련 지급 건 정보를 함께 돌려준다")
  void detailIncludesParticipationAndPayouts() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .memo("도배")
                .build());
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    var detail = reportService.detail(operator, report.getId());

    assertThat(detail.reportId()).isEqualTo(report.getId().toString());
    assertThat(detail.eventId()).isEqualTo(event.getId().toString());
    assertThat(detail.memo()).isEqualTo("도배");
    assertThat(detail.payouts()).hasSize(1);
    assertThat(detail.payouts().get(0).payoutId()).isEqualTo(payout.getId().toString());
    assertThat(detail.payouts().get(0).payoutType()).isEqualTo("TOP_LIKE");
  }

  @Test
  @DisplayName("지급 건이 없는 참여의 신고 상세는 payouts가 빈 목록이다")
  void detailWithNoPayouts() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .memo("도배")
                .build());

    var detail = reportService.detail(operator, report.getId());

    assertThat(detail.reportId()).isEqualTo(report.getId().toString());
    assertThat(detail.payouts()).isEmpty();
  }

  @Test
  @DisplayName("없는 신고 상세 조회는 404다")
  void detailNotFound() {
    assertThatThrownBy(() -> reportService.detail(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("신고 인정(HOLD)은 상태를 UPHELD로 바꾸고 관련 지급을 보류한다")
  void upholdMarksUpheldAndHoldsPayouts() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    var decision =
        reportService.uphold(
            operator,
            report.getId(),
            new ReportUpholdReqDto("근거 확인됨", ReportUpholdAction.HOLD, report.getRevision()),
            null);

    assertThat(decision.status()).isEqualTo("UPHELD");
    ZoneEventReport reloaded = reportRepository.findById(report.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.UPHELD);
    assertThat(reloaded.getDecisionNote()).isEqualTo("근거 확인됨");
    assertThat(rewardPayoutRepository.findById(payout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.HELD_REPORT);
  }

  @Test
  @DisplayName("action=DISQUALIFY는 아직 지원하지 않으므로 400이다")
  void upholdDisqualifyNotSupported() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());

    assertThatThrownBy(
            () ->
                reportService.uphold(
                    operator,
                    report.getId(),
                    new ReportUpholdReqDto(
                        "근거", ReportUpholdAction.DISQUALIFY, report.getRevision()),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.report.action_not_supported");
  }

  @Test
  @DisplayName("expectedRevision이 다르면 409, 이미 처리된 신고면 409다")
  void upholdConflicts() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());

    assertThatThrownBy(
            () ->
                reportService.uphold(
                    operator,
                    report.getId(),
                    new ReportUpholdReqDto("근거", ReportUpholdAction.HOLD, report.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class);

    reportService.uphold(
        operator,
        report.getId(),
        new ReportUpholdReqDto("근거", ReportUpholdAction.HOLD, report.getRevision()),
        null);
    ZoneEventReport decided = reportRepository.findById(report.getId()).orElseThrow();
    assertThatThrownBy(
            () ->
                reportService.uphold(
                    operator,
                    report.getId(),
                    new ReportUpholdReqDto("근거", ReportUpholdAction.HOLD, decided.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("신고 기각은 상태를 DISMISSED로 바꾸지만 지급 보류는 그대로 둔다(별도 release-hold 필요)")
  void dismissMarksDismissedButDoesNotReleaseHold() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.hold();
    rewardPayoutRepository.saveAndFlush(payout);

    var decision =
        reportService.dismiss(
            operator,
            report.getId(),
            new ReportDismissReqDto("촬영 조건 충족 확인, 신고 근거 부족", report.getRevision()),
            null);

    assertThat(decision.status()).isEqualTo("DISMISSED");
    assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus())
        .isEqualTo(ReportStatus.DISMISSED);
    assertThat(rewardPayoutRepository.findById(payout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.HELD_REPORT);
  }

  @Test
  @DisplayName("이미 처리된 신고 기각은 409다")
  void dismissAlreadyDecidedConflicts() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    reportService.dismiss(
        operator, report.getId(), new ReportDismissReqDto("사유", report.getRevision()), null);
    ZoneEventReport decided = reportRepository.findById(report.getId()).orElseThrow();

    assertThatThrownBy(
            () ->
                reportService.dismiss(
                    operator,
                    report.getId(),
                    new ReportDismissReqDto("사유", decided.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class);
  }

  private ZoneEventParticipation participation() {
    return participationRepository.save(
        ZoneEventParticipation.builder()
            .event(event)
            .userId(UUID.randomUUID())
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build());
  }
}
