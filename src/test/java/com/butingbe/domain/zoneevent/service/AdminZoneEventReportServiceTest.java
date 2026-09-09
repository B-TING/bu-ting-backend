package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.ReportDismissReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdAction;
import com.butingbe.domain.zoneevent.dto.request.ReportUpholdReqDto;
import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
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
import com.butingbe.domain.zoneevent.repository.IdempotencyRecordRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
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
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private EntityManager entityManager;

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
  @DisplayName("이미 발송 완료(SENT)된 TOP_LIKE 지급은 신고 인정으로도 보류하지 않는다")
  void upholdDoesNotHoldAlreadySentTopLikePayout() {
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
    ReflectionTestUtils.setField(payout, "status", RewardPayoutStatus.SENT);
    rewardPayoutRepository.saveAndFlush(payout);

    var decision =
        reportService.uphold(
            operator,
            report.getId(),
            new ReportUpholdReqDto("근거 확인됨", ReportUpholdAction.HOLD, report.getRevision()),
            null);

    assertThat(decision.status()).isEqualTo("UPHELD");
    assertThat(rewardPayoutRepository.findById(payout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.NONE);
  }

  @Test
  @DisplayName("이미 지급 완료(PAID)된 기본 보상은 신고 인정으로도 보류하지 않는다")
  void upholdDoesNotHoldAlreadyPaidBasePayout() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    BaseRewardPayout basePayout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    ReflectionTestUtils.setField(basePayout, "status", BaseRewardPayoutStatus.PAID);
    baseRewardPayoutRepository.saveAndFlush(basePayout);

    var decision =
        reportService.uphold(
            operator,
            report.getId(),
            new ReportUpholdReqDto("근거 확인됨", ReportUpholdAction.HOLD, report.getRevision()),
            null);

    assertThat(decision.status()).isEqualTo("UPHELD");
    assertThat(
            baseRewardPayoutRepository.findById(basePayout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.NONE);
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

  @Test
  @DisplayName("status가 null이면 필터링 없이 전체 신고를 반환한다")
  void listWithNullStatusReturnsAll() {
    ZoneEventParticipation p1 = participation();
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p1.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());

    var page = reportService.list(operator, null, null, null, null, 1, 20);

    assertThat(page.items()).isNotEmpty();
  }

  @Test
  @DisplayName("신고의 participationId가 존재하지 않는 참여를 가리키면 상세 조회는 404다")
  void detailParticipationNotFound() {
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(UUID.randomUUID())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());

    assertThatThrownBy(() -> reportService.detail(operator, report.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.participation.not_found");
  }

  @Test
  @DisplayName("신고 상세는 BASE 지급 건도 함께 돌려준다")
  void detailIncludesBasePayout() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    BaseRewardPayout basePayout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    var detail = reportService.detail(operator, report.getId());

    assertThat(detail.payouts()).hasSize(1);
    assertThat(detail.payouts().get(0).payoutId()).isEqualTo(basePayout.getId().toString());
    assertThat(detail.payouts().get(0).payoutType()).isEqualTo("BASE");
  }

  @Test
  @DisplayName("신고 인정(uphold) 재생 응답이 손상된 JSON이면 500 대신 명확한 예외를 던진다")
  void upholdReplayWithCorruptedJsonThrows() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    String key = "idem-corrupt-" + UUID.randomUUID();
    ReportUpholdReqDto request =
        new ReportUpholdReqDto("근거", ReportUpholdAction.HOLD, report.getRevision());
    String fingerprint =
        report.getId()
            + ":"
            + request.note()
            + ":"
            + request.action()
            + ":"
            + request.expectedRevision();
    idempotencyRecordRepository.save(
        new IdempotencyRecord(key, "zone-event-report-uphold", fingerprint, "not-a-json"));

    assertThatThrownBy(() -> reportService.uphold(operator, report.getId(), request, key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to deserialize");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 신고 인정을 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void upholdIsIdempotent() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    String key = "idem-" + UUID.randomUUID();
    ReportUpholdReqDto request =
        new ReportUpholdReqDto("근거", ReportUpholdAction.HOLD, report.getRevision());

    var first = reportService.uphold(operator, report.getId(), request, key);
    var replay = reportService.uphold(operator, report.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 신고 기각을 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void dismissIsIdempotent() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    String key = "idem-" + UUID.randomUUID();
    ReportDismissReqDto request = new ReportDismissReqDto("사유", report.getRevision());

    var first = reportService.dismiss(operator, report.getId(), request, key);
    var replay = reportService.dismiss(operator, report.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("매뉴얼 체크 통과 후에도 실제 flush 시점에 다른 트랜잭션이 이미 revision을 올렸다면 409다(신고 기각, 진짜 낙관적 락 충돌)")
  void dismissFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    Long revisionSeenByCaller = report.getRevision();

    // 이 서비스 호출이 붙잡고 있는 영속성 컨텍스트가 모르는 사이, 다른 트랜잭션이 같은 row의 revision을 이미 올렸다고 가정한다.
    // 네이티브 쿼리로 DB만 바꾸면 1차 캐시에 남아있는 report 엔티티는 여전히 예전 revision을 들고 있으므로,
    // 매뉴얼 체크(expectedRevision)는 통과하지만 실제 saveAndFlush의 버전 체크는 실패한다.
    entityManager
        .createNativeQuery(
            "UPDATE zone_event_report SET revision = revision + 1 WHERE report_id = :id")
        .setParameter("id", report.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                reportService.dismiss(
                    operator,
                    report.getId(),
                    new ReportDismissReqDto("사유", revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.report.stale_revision");
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
