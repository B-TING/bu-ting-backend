package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.request.ReleaseHoldReqDto;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
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
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminRewardPayoutServiceTest extends AbstractContainerTest {

  @Autowired private AdminRewardPayoutService payoutService;
  @Autowired private RewardPayoutRepository rewardPayoutRepository;
  @Autowired private BaseRewardPayoutRepository baseRewardPayoutRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private EntityManager entityManager;

  @Autowired
  private com.butingbe.domain.reward.repository.RewardGrantRepository rewardGrantRepository;

  @Autowired
  private com.butingbe.domain.reward.repository.UserPointBalanceRepository
      userPointBalanceRepository;

  @Autowired
  private com.butingbe.domain.reward.repository.RewardCatalogRepository rewardCatalogRepository;

  private ZoneEvent event;
  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    // NOTE: 테스트 프로파일은 flyway.enabled=false(ddl-auto=create-drop)라 V33 카탈로그 시드가 적용되지
    // 않는다. RewardServiceTest와 같은 방식으로 POINT_BASE 카탈로그를 직접 심어야 mark-sent(BASE)의
    // rewardService.grantBaseReward 호출이 카탈로그를 찾을 수 있다.
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_BASE")
            .name("기본 포인트")
            .pointAmount(50)
            .build());
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
  @DisplayName("미해결 신고가 없으면 TOP_LIKE 지급의 보류를 해제한다")
  void releasesTopLikeHoldWhenNoUnresolvedReports() {
    ZoneEventParticipation p = participation();
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

    var result =
        payoutService.releaseHold(
            operator,
            payout.getId(),
            new ReleaseHoldReqDto("최종 확인 완료", payout.getRevision()),
            null);

    assertThat(result.holdStatus()).isEqualTo("NONE");
    assertThat(rewardPayoutRepository.findById(payout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.NONE);
  }

  @Test
  @DisplayName("미해결 신고가 남아있으면 보류를 해제할 수 없다(409)")
  void refusesReleaseWhileUnresolvedReportsRemain() {
    ZoneEventParticipation p = participation();
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
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());

    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator,
                    payout.getId(),
                    new ReleaseHoldReqDto("확인", payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.unresolved_reports_remain");
  }

  @Test
  @DisplayName("두 신고 중 하나만 기각되고 나머지가 미해결이면 보류를 해제할 수 없다(409)")
  void refusesReleaseWhenOneOfTwoReportsStillUnresolved() {
    ZoneEventParticipation p = participation();
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
    ZoneEventReport dismissedReport =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(p.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    dismissedReport.resolveAs(ReportStatus.DISMISSED);
    reportRepository.saveAndFlush(dismissedReport);
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.OTHER)
            .build());

    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator,
                    payout.getId(),
                    new ReleaseHoldReqDto("확인", payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.unresolved_reports_remain");
  }

  @Test
  @DisplayName("보류 중이 아닌 지급의 release-hold는 409다")
  void refusesReleaseWhenNotHeld() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator,
                    payout.getId(),
                    new ReleaseHoldReqDto("확인", payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.not_held");
  }

  @Test
  @DisplayName("BASE 지급도 같은 방식으로 보류 해제된다")
  void releasesBaseHold() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.hold();
    baseRewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.releaseHold(
            operator, payout.getId(), new ReleaseHoldReqDto("확인", payout.getRevision()), null);

    assertThat(result.payoutType()).isEqualTo("BASE");
    assertThat(baseRewardPayoutRepository.findById(payout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.NONE);
  }

  @Test
  @DisplayName("없는 지급 건의 release-hold는 404다")
  void notFound() {
    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator, UUID.randomUUID(), new ReleaseHoldReqDto("확인", 0L), null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("expectedRevision이 다르면 매뉴얼 체크에서 바로 409다(DB 경합 없이)")
  void releaseHoldStaleExpectedRevisionConflicts() {
    ZoneEventParticipation p = participation();
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

    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator,
                    payout.getId(),
                    new ReleaseHoldReqDto("확인", payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("매뉴얼 체크 통과 후에도 실제 flush 시점에 다른 트랜잭션이 이미 revision을 올렸다면 409다(TOP_LIKE, 진짜 낙관적 락 충돌)")
  void releaseTopLikeHoldFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
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
    Long revisionSeenByCaller = payout.getRevision();

    // 이 서비스 호출이 붙잡고 있는 영속성 컨텍스트가 모르는 사이, 다른 트랜잭션이 같은 row의 revision을 이미 올렸다고 가정한다.
    // 네이티브 쿼리로 DB만 바꾸면 1차 캐시에 남아있는 payout 엔티티는 여전히 예전 revision을 들고 있으므로,
    // 매뉴얼 체크(expectedRevision)는 통과하지만 실제 saveAndFlush의 버전 체크는 실패한다.
    entityManager
        .createNativeQuery("UPDATE reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator,
                    payout.getId(),
                    new ReleaseHoldReqDto("확인", revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("매뉴얼 체크 통과 후에도 실제 flush 시점에 다른 트랜잭션이 이미 revision을 올렸다면 409다(BASE, 진짜 낙관적 락 충돌)")
  void releaseBaseHoldFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.hold();
    baseRewardPayoutRepository.saveAndFlush(payout);
    Long revisionSeenByCaller = payout.getRevision();

    entityManager
        .createNativeQuery(
            "UPDATE base_reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.releaseHold(
                    operator,
                    payout.getId(),
                    new ReleaseHoldReqDto("확인", revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 release-hold를 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void releaseHoldIsIdempotent() {
    ZoneEventParticipation p = participation();
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
    String key = "idem-" + UUID.randomUUID();
    ReleaseHoldReqDto request = new ReleaseHoldReqDto("확인", payout.getRevision());

    var first = payoutService.releaseHold(operator, payout.getId(), request, key);
    var replay = payoutService.releaseHold(operator, payout.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("저장된 재생 응답이 손상된 JSON이면 500 대신 명확한 예외를 던진다")
  void releaseHoldReplayWithCorruptedJsonThrows() {
    ZoneEventParticipation p = participation();
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
    String key = "idem-corrupt-" + UUID.randomUUID();
    ReleaseHoldReqDto request = new ReleaseHoldReqDto("확인", payout.getRevision());
    String fingerprint = payout.getId() + ":" + request.note() + ":" + request.expectedRevision();
    idempotencyRecordRepository.save(
        new IdempotencyRecord(key, "reward-payout-release-hold", fingerprint, "not-a-json"));

    assertThatThrownBy(() -> payoutService.releaseHold(operator, payout.getId(), request, key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to deserialize");
  }

  @Test
  @DisplayName("TOP_LIKE 지급 상세를 조회한다")
  void detailReturnsTopLike() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    var result = payoutService.detail(operator, payout.getId());

    assertThat(result.payoutType()).isEqualTo("TOP_LIKE");
    assertThat(result.eventId()).isEqualTo(event.getId().toString());
    assertThat(result.rankN()).isEqualTo(1);
    assertThat(result.status()).isEqualTo("PENDING_ASSIGN");
  }

  @Test
  @DisplayName("BASE 지급 상세는 참여를 통해 eventId를 채워 돌려준다")
  void detailReturnsBaseWithEventId() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    var result = payoutService.detail(operator, payout.getId());

    assertThat(result.payoutType()).isEqualTo("BASE");
    assertThat(result.eventId()).isEqualTo(event.getId().toString());
    assertThat(result.rankN()).isNull();
    assertThat(result.status()).isEqualTo("PENDING_CONFIRM");
  }

  @Test
  @DisplayName("없는 지급 건 상세 조회는 404다")
  void detailNotFound() {
    assertThatThrownBy(() -> payoutService.detail(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("rewardReason=BASE면 BASE 지급만 페이징 조회한다")
  void listFiltersByBaseRewardReason() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(p1.getId())
            .reward(new RewardSnapshot(50, null, null, null))
            .build());
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(p2.getId())
            .reward(new RewardSnapshot(50, null, null, null))
            .build());
    rewardPayoutRepository.save(
        RewardPayout.builder()
            .eventId(event.getId())
            .participationId(participation().getId())
            .rankN(1)
            .likeCountAtClose(1L)
            .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
            .build());

    var result = payoutService.list(operator, null, null, "BASE", null, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(2);
    assertThat(result.items()).allMatch(i -> i.payoutType().equals("BASE"));
  }

  @Test
  @DisplayName("rewardReason=TOP_LIKE면 TOP_LIKE 지급만 페이징 조회한다")
  void listFiltersByTopLikeRewardReason() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    rewardPayoutRepository.save(
        RewardPayout.builder()
            .eventId(event.getId())
            .participationId(p1.getId())
            .rankN(1)
            .likeCountAtClose(1L)
            .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
            .build());
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(p2.getId())
            .reward(new RewardSnapshot(50, null, null, null))
            .build());

    var result =
        payoutService.list(operator, null, null, "TOP_LIKE", null, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items()).allMatch(i -> i.payoutType().equals("TOP_LIKE"));
  }

  @Test
  @DisplayName("rewardReason 없이 조회하면 두 타입을 병합해서 돌려준다")
  void listMergesBothTypesWhenRewardReasonOmitted() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(p1.getId())
            .reward(new RewardSnapshot(50, null, null, null))
            .build());
    rewardPayoutRepository.save(
        RewardPayout.builder()
            .eventId(event.getId())
            .participationId(p2.getId())
            .rankN(1)
            .likeCountAtClose(1L)
            .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
            .build());

    var result = payoutService.list(operator, null, null, null, null, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(2);
    assertThat(result.items().stream().map(i -> i.payoutType()).distinct().count()).isEqualTo(2);
  }

  @Test
  @DisplayName("rewardReason 없이 status만 지정하면 400이다(타입별 상태 enum이 달라 모호함)")
  void listRejectsStatusWithoutRewardReason() {
    assertThatThrownBy(
            () ->
                payoutService.list(
                    operator, null, null, null, "CONFIRMED", null, null, null, 1, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.reward.payout.status_requires_reward_reason");
  }

  @Test
  @DisplayName("BASE 목록 항목은 참여를 통해 eventId를 채운다")
  void listBaseItemsIncludeEventId() {
    ZoneEventParticipation p = participation();
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(p.getId())
            .reward(new RewardSnapshot(50, null, null, null))
            .build());

    var result = payoutService.list(operator, null, null, "BASE", null, null, null, null, 1, 20);

    assertThat(result.items().get(0).eventId()).isEqualTo(event.getId().toString());
  }

  @Test
  @DisplayName("PENDING_ASSIGN인 TOP_LIKE에 prizeRewardCode를 채우면 PENDING_CONFIRM으로 자동 전진한다")
  void updateAssignsRewardAndAdvancesTopLikeToPendingConfirm() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());

    var result =
        payoutService.update(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                new RewardSnapshot(null, null, 1, "COUPON_TOP"),
                "1등 상품 확정",
                null,
                payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("PENDING_CONFIRM");
    assertThat(result.memo()).isEqualTo("1등 상품 확정");
  }

  @Test
  @DisplayName("이미 CONFIRMED인 지급의 reward를 바꾸려 하면 409다")
  void updateRewardAfterConfirmConflicts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        new RewardSnapshot(100, null, null, null),
                        null,
                        null,
                        payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.reward_locked");
  }

  @Test
  @DisplayName("CONFIRMED 이후에도 memo·scheduledAt은 바꿀 수 있다")
  void updateMemoAndScheduleAfterConfirmStillAllowed() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(1);

    var result =
        payoutService.update(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                null, "발송 예정", schedule, payout.getRevision()),
            null);

    assertThat(result.memo()).isEqualTo("발송 예정");
    assertThat(result.scheduledAt()).isEqualTo(schedule);
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 update를 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void updateIsIdempotent() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    String key = "idem-update-" + UUID.randomUUID();
    var request =
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
            null, "메모", null, payout.getRevision());

    var first = payoutService.update(operator, payout.getId(), request, key);
    var replay = payoutService.update(operator, payout.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("update: TOP_LIKE의 expectedRevision이 다르면 409다")
  void updateTopLikeStaleRevisionConflicts() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        null, null, null, payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("update: 이미 CONFIRMED인 TOP_LIKE의 reward를 바꾸려 하면 409다")
  void updateTopLikeRewardLockedAfterConfirm() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        new RewardSnapshot(null, null, 1, "COUPON_TOP2"),
                        null,
                        null,
                        payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.reward_locked");
  }

  @Test
  @DisplayName("update: TOP_LIKE도 scheduledAt만 바꿀 수 있다")
  void updateTopLikeScheduleOnly() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(2);

    var result =
        payoutService.update(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                null, null, schedule, payout.getRevision()),
            null);

    assertThat(result.scheduledAt()).isEqualTo(schedule);
  }

  @Test
  @DisplayName("update: 매뉴얼 체크 통과 후 flush 시점에 TOP_LIKE revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void updateTopLikeFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery("UPDATE reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        null, "메모", null, revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("update: BASE의 expectedRevision이 다르면 409다")
  void updateBaseStaleRevisionConflicts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        null, null, null, payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("update: 아직 확정 전인 BASE는 reward를 바꿀 수 있다")
  void updateBaseRewardWhilePendingConfirm() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    var result =
        payoutService.update(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                new RewardSnapshot(100, "BADGE1", null, null), null, null, payout.getRevision()),
            null);

    assertThat(result.reward().points()).isEqualTo(100);
  }

  @Test
  @DisplayName("update: 매뉴얼 체크 통과 후 flush 시점에 BASE revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void updateBaseFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery(
            "UPDATE base_reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.update(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto(
                        null, "메모", null, revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("일괄 확정: PENDING_CONFIRM인 건들을 모두 CONFIRMED로 바꾼다")
  void bulkConfirmsAllPendingConfirmPayouts() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout base =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p1.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    RewardPayout topLike =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p2.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    topLike.assignReward(new RewardSnapshot(null, null, 1, "COUPON_TOP"));
    rewardPayoutRepository.saveAndFlush(topLike);

    var result =
        payoutService.bulkConfirm(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                List.of(base.getId().toString(), topLike.getId().toString()),
                Map.of(
                    base.getId().toString(),
                    base.getRevision(),
                    topLike.getId().toString(),
                    topLike.getRevision())),
            null);

    assertThat(result.processedPayoutIds()).hasSize(2);
    assertThat(baseRewardPayoutRepository.findById(base.getId()).orElseThrow().getStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.CONFIRMED);
    assertThat(rewardPayoutRepository.findById(topLike.getId()).orElseThrow().getStatus())
        .isEqualTo(RewardPayoutStatus.CONFIRMED);
  }

  @Test
  @DisplayName("일괄 확정: 하나라도 조건 미충족이면 전부 반영하지 않고 문제 id 목록과 함께 409다")
  void bulkConfirmAllOrNothingOnConflict() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout ok =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p1.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    BaseRewardPayout held =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p2.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    held.hold();
    baseRewardPayoutRepository.saveAndFlush(held);

    assertThatThrownBy(
            () ->
                payoutService.bulkConfirm(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                        List.of(ok.getId().toString(), held.getId().toString()),
                        Map.of(
                            ok.getId().toString(),
                            ok.getRevision(),
                            held.getId().toString(),
                            held.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class)
        .satisfies(
            e ->
                assertThat(
                        ((com.butingbe.global.error.exception.BulkPayoutConflictException) e)
                            .getProblemPayoutIds())
                    .containsExactly(held.getId().toString()));
    assertThat(baseRewardPayoutRepository.findById(ok.getId()).orElseThrow().getStatus())
        .isEqualTo(com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.PENDING_CONFIRM);
  }

  @Test
  @DisplayName("일괄 확정: 아직 상품 코드가 없는 TOP_LIKE(PENDING_ASSIGN)는 문제 목록에 포함된다")
  void bulkConfirmRejectsUnassignedTopLike() {
    ZoneEventParticipation p = participation();
    RewardPayout topLike =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.bulkConfirm(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                        List.of(topLike.getId().toString()),
                        Map.of(topLike.getId().toString(), topLike.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class);
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 일괄 확정을 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void bulkConfirmIsIdempotent() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    String key = "idem-bulk-confirm-" + UUID.randomUUID();
    var request =
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
            List.of(payout.getId().toString()),
            Map.of(payout.getId().toString(), payout.getRevision()));

    var first = payoutService.bulkConfirm(operator, request, key);
    var replay = payoutService.bulkConfirm(operator, request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("일괄 확정: TOP_LIKE·BASE 각각의 개별 실패 사유(오래된 revision·보류 중·존재하지 않음·상태 불일치)가 모두 문제 목록에 담긴다")
  void bulkConfirmCollectsAllDistinctValidationFailures() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    ZoneEventParticipation p3 = participation();
    ZoneEventParticipation p4 = participation();
    RewardPayout topLikeStale =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p1.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    RewardPayout topLikeHeld =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p2.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    topLikeHeld.hold();
    rewardPayoutRepository.saveAndFlush(topLikeHeld);
    BaseRewardPayout baseStale =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p3.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    BaseRewardPayout baseWrongStatus =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p4.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    baseWrongStatus.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(baseWrongStatus);
    UUID missingId = UUID.randomUUID();

    var ids =
        List.of(
            topLikeStale.getId().toString(),
            topLikeHeld.getId().toString(),
            missingId.toString(),
            baseStale.getId().toString(),
            baseWrongStatus.getId().toString());
    var expectedRevisions =
        Map.of(
            topLikeStale.getId().toString(),
            topLikeStale.getRevision() + 1,
            topLikeHeld.getId().toString(),
            topLikeHeld.getRevision(),
            baseStale.getId().toString(),
            baseStale.getRevision() + 1,
            baseWrongStatus.getId().toString(),
            baseWrongStatus.getRevision());

    assertThatThrownBy(
            () ->
                payoutService.bulkConfirm(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                        ids, expectedRevisions),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class)
        .satisfies(
            e ->
                assertThat(
                        ((com.butingbe.global.error.exception.BulkPayoutConflictException) e)
                            .getProblemPayoutIds())
                    .containsExactlyInAnyOrder(
                        topLikeStale.getId().toString(),
                        topLikeHeld.getId().toString(),
                        missingId.toString(),
                        baseStale.getId().toString(),
                        baseWrongStatus.getId().toString()));
  }

  @Test
  @DisplayName("일괄 일정: CONFIRMED인 건들의 scheduledAt을 한 번에 바꾼다")
  void bulkSchedulesConfirmedPayouts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(3);

    var result =
        payoutService.bulkSchedule(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
                List.of(payout.getId().toString()),
                schedule,
                Map.of(payout.getId().toString(), payout.getRevision())),
            null);

    assertThat(result.processedPayoutIds()).containsExactly(payout.getId().toString());
    assertThat(baseRewardPayoutRepository.findById(payout.getId()).orElseThrow().getScheduledAt())
        .isEqualTo(schedule);
  }

  @Test
  @DisplayName("일괄 일정: BASE와 TOP_LIKE가 섞여 있어도 CONFIRMED인 건들의 scheduledAt을 한 번에 바꾼다")
  void bulkSchedulesConfirmedPayoutsOfBothTypes() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout base =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p1.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    base.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(base);
    RewardPayout topLike =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p2.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    topLike.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(topLike);
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(3);

    var result =
        payoutService.bulkSchedule(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
                List.of(base.getId().toString(), topLike.getId().toString()),
                schedule,
                Map.of(
                    base.getId().toString(), base.getRevision(),
                    topLike.getId().toString(), topLike.getRevision())),
            null);

    assertThat(result.processedPayoutIds()).hasSize(2);
    assertThat(baseRewardPayoutRepository.findById(base.getId()).orElseThrow().getScheduledAt())
        .isEqualTo(schedule);
    assertThat(rewardPayoutRepository.findById(topLike.getId()).orElseThrow().getScheduledAt())
        .isEqualTo(schedule);
  }

  @Test
  @DisplayName("일괄 일정: 아직 CONFIRMED가 아닌 건이 섞여 있으면 전부 반영하지 않고 409다")
  void bulkScheduleRejectsNotYetConfirmed() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout confirmed =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p1.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    confirmed.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(confirmed);
    BaseRewardPayout notConfirmed =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p2.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.bulkSchedule(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
                        List.of(confirmed.getId().toString(), notConfirmed.getId().toString()),
                        OffsetDateTime.now().plusDays(1),
                        Map.of(
                            confirmed.getId().toString(), confirmed.getRevision(),
                            notConfirmed.getId().toString(), notConfirmed.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class);
    assertThat(
            baseRewardPayoutRepository.findById(confirmed.getId()).orElseThrow().getScheduledAt())
        .isNull();
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 일괄 일정을 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void bulkScheduleIsIdempotent() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    String key = "idem-bulk-schedule-" + UUID.randomUUID();
    OffsetDateTime schedule = OffsetDateTime.now().plusDays(1);
    var request =
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
            List.of(payout.getId().toString()),
            schedule,
            Map.of(payout.getId().toString(), payout.getRevision()));

    var first = payoutService.bulkSchedule(operator, request, key);
    var replay = payoutService.bulkSchedule(operator, request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("일괄 일정: TOP_LIKE·BASE 각각의 개별 실패 사유(오래된 revision·보류 중·상태 불일치·존재하지 않음)가 모두 문제 목록에 담긴다")
  void bulkScheduleCollectsAllDistinctValidationFailures() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    ZoneEventParticipation p3 = participation();
    ZoneEventParticipation p4 = participation();
    ZoneEventParticipation p5 = participation();
    RewardPayout topLikeStale =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p1.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    topLikeStale.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(topLikeStale);
    RewardPayout topLikeHeld =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p2.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    topLikeHeld.confirm(operator.id());
    topLikeHeld.hold();
    rewardPayoutRepository.saveAndFlush(topLikeHeld);
    RewardPayout topLikeWrongStatus =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p3.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    BaseRewardPayout baseStale =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p4.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    baseStale.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(baseStale);
    BaseRewardPayout baseHeld =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p5.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    baseHeld.confirm(operator.id());
    baseHeld.hold();
    baseRewardPayoutRepository.saveAndFlush(baseHeld);
    UUID missingId = UUID.randomUUID();

    var ids =
        List.of(
            topLikeStale.getId().toString(),
            topLikeHeld.getId().toString(),
            topLikeWrongStatus.getId().toString(),
            missingId.toString(),
            baseStale.getId().toString(),
            baseHeld.getId().toString());
    var expectedRevisions =
        Map.of(
            topLikeStale.getId().toString(), topLikeStale.getRevision() + 1,
            topLikeHeld.getId().toString(), topLikeHeld.getRevision(),
            topLikeWrongStatus.getId().toString(), topLikeWrongStatus.getRevision(),
            baseStale.getId().toString(), baseStale.getRevision() + 1,
            baseHeld.getId().toString(), baseHeld.getRevision());

    assertThatThrownBy(
            () ->
                payoutService.bulkSchedule(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto(
                        ids, OffsetDateTime.now().plusDays(1), expectedRevisions),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class)
        .satisfies(
            e ->
                assertThat(
                        ((com.butingbe.global.error.exception.BulkPayoutConflictException) e)
                            .getProblemPayoutIds())
                    .containsExactlyInAnyOrder(
                        topLikeStale.getId().toString(),
                        topLikeHeld.getId().toString(),
                        topLikeWrongStatus.getId().toString(),
                        missingId.toString(),
                        baseStale.getId().toString(),
                        baseHeld.getId().toString()));
  }

  @Test
  @DisplayName("mark-mail-sent: CONFIRMED인 TOP_LIKE를 MAIL_SENT로 바꾼다")
  void marksMailSent() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markMailSent(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                payout.getId(), null, "메일 발송함", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("MAIL_SENT");
    assertThat(result.memo()).isEqualTo("메일 발송함");
    assertThat(result.mailedAt()).isNotNull();
  }

  @Test
  @DisplayName("mark-mail-sent: BASE 지급 건에 호출하면 409(wrong_type)다")
  void markMailSentRejectsBaseType() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.wrong_type");
  }

  @Test
  @DisplayName("mark-mail-sent: CONFIRMED가 아니면 409(invalid_state)다")
  void markMailSentRejectsWrongStatus() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 mark-mail-sent를 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void markMailSentIsIdempotent() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);
    String key = "idem-mark-mail-sent-" + UUID.randomUUID();
    var request =
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
            payout.getId(), null, "발송", payout.getRevision());

    var first = payoutService.markMailSent(operator, request, key);
    var replay = payoutService.markMailSent(operator, request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("mark-mail-sent: expectedRevision이 다르면 409다")
  void markMailSentStaleRevisionConflicts() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("mark-mail-sent: 보류 중인 지급은 409(invalid_state)다")
  void markMailSentBlockedWhileHeld() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.hold();
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-mail-sent: 매뉴얼 체크 통과 후 flush 시점에 revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void markMailSentFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery("UPDATE reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.markMailSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("mark-info-collected: MAIL_SENT인 TOP_LIKE를 INFO_COLLECTED로 바꾼다")
  void marksInfoCollected() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markInfoCollected(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                payout.getId(), null, "주소 수집 완료", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("INFO_COLLECTED");
    assertThat(result.informationCollectedAt()).isNotNull();
  }

  @Test
  @DisplayName("mark-info-collected: BASE 지급 건에 호출하면 409(wrong_type)다")
  void markInfoCollectedRejectsBaseType() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.markInfoCollected(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.wrong_type");
  }

  @Test
  @DisplayName("mark-info-collected: MAIL_SENT가 아니면 409(invalid_state)다")
  void markInfoCollectedRejectsWrongStatus() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markInfoCollected(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto(
                        payout.getId(), null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-sent(BASE): CONFIRMED를 PAID로 바꾸고 같은 트랜잭션에서 reward_grant·포인트 잔액에 원자적으로 반영한다")
  void marksBaseSentAndGrantsAtomically() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markSent(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                payout.getId(), null, null, "지급 완료", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("PAID");
    assertThat(result.paidAt()).isNotNull();
    assertThat(
            rewardGrantRepository.existsByParticipationIdAndGrantReasonAndReward_Id(
                p.getId(),
                com.butingbe.domain.reward.entity.GrantReason.BASE,
                rewardCatalogRepository.findByCode("POINT_BASE").orElseThrow().getId()))
        .isTrue();
    assertThat(userPointBalanceRepository.findById(p.getUserId()).orElseThrow().getBalance())
        .isEqualTo(50);
  }

  @Test
  @DisplayName("mark-sent(BASE): 재시도로 두 번 호출돼도 이중 지급되지 않는다(UK 가드)")
  void marksBaseSentTwiceIsSafe() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    payoutService.markSent(
        operator,
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
            payout.getId(), null, null, "1차 발송", payout.getRevision()),
        null);
    payout = baseRewardPayoutRepository.findById(payout.getId()).orElseThrow();
    // FAILED로 되돌린 뒤 재시도 상황을 흉내낸다 (retry()는 Task 9에서 추가되므로 여기서는 직접 상태를 되돌린다).
    org.springframework.test.util.ReflectionTestUtils.setField(
        payout, "status", com.butingbe.domain.reward.entity.BaseRewardPayoutStatus.CONFIRMED);
    baseRewardPayoutRepository.saveAndFlush(payout);

    payoutService.markSent(
        operator,
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
            payout.getId(), null, null, "2차 발송", payout.getRevision()),
        null);

    assertThat(
            rewardGrantRepository.countByParticipationIdAndGrantReasonAndReward_Id(
                p.getId(),
                com.butingbe.domain.reward.entity.GrantReason.BASE,
                rewardCatalogRepository.findByCode("POINT_BASE").orElseThrow().getId()))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("mark-sent(TOP_LIKE): INFO_COLLECTED를 SENT로 바꾸고 reference를 저장한다")
  void marksTopLikeSent() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    payout.markInfoCollected(OffsetDateTime.now(), null);
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.markSent(
            operator,
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                payout.getId(), null, "REF-001", "발송함", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("SENT");
    assertThat(result.reference()).isEqualTo("REF-001");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 mark-sent를 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void markSentIsIdempotent() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    String key = "idem-mark-sent-" + UUID.randomUUID();
    var request =
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
            payout.getId(), null, null, "지급 완료", payout.getRevision());

    var first = payoutService.markSent(operator, request, key);
    var replay = payoutService.markSent(operator, request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("mark-sent(TOP_LIKE): expectedRevision이 다르면 409다")
  void markTopLikeSentStaleRevisionConflicts() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    payout.markInfoCollected(OffsetDateTime.now(), null);
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("mark-sent(TOP_LIKE): 보류 중인 지급은 409(invalid_state)다")
  void markTopLikeSentBlockedWhileHeld() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    payout.markInfoCollected(OffsetDateTime.now(), null);
    payout.hold();
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-sent(TOP_LIKE): INFO_COLLECTED가 아니면 409(invalid_state)다")
  void markTopLikeSentRejectsWrongStatus() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-sent(TOP_LIKE): 매뉴얼 체크 통과 후 flush 시점에 revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void markTopLikeSentFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.markMailSent(OffsetDateTime.now(), null);
    payout.markInfoCollected(OffsetDateTime.now(), null);
    rewardPayoutRepository.saveAndFlush(payout);
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery("UPDATE reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, "REF", null, revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("mark-sent(BASE): expectedRevision이 다르면 409다")
  void markBaseSentStaleRevisionConflicts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("mark-sent(BASE): 보류 중인 지급은 409(invalid_state)다")
  void markBaseSentBlockedWhileHeld() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    payout.hold();
    baseRewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-sent(BASE): CONFIRMED가 아니면 409(invalid_state)다")
  void markBaseSentRejectsWrongStatus() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("mark-sent(BASE): 참여를 찾을 수 없으면 404다")
  void markBaseSentParticipationNotFound() {
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(UUID.randomUUID())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, payout.getRevision()),
                    null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("mark-sent(BASE): 매뉴얼 체크 통과 후 flush 시점에 revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void markBaseSentFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery(
            "UPDATE base_reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.markSent(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto(
                        payout.getId(), null, null, null, revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("retry: FAILED인 지급을 CONFIRMED로 되돌리고 failureCode를 지운다")
  void retriesFailedPayout() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    baseRewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.retry(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                "재시도", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("CONFIRMED");
    assertThat(result.failureCode()).isNull();
  }

  @Test
  @DisplayName("retry: FAILED가 아니면 409다")
  void retryRejectsNonFailedStatus() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                        null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("retry: TOP_LIKE FAILED인 지급을 CONFIRMED로 되돌리고 failureCode를 지운다")
  void retriesFailedTopLikePayout() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(3L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    rewardPayoutRepository.saveAndFlush(payout);

    var result =
        payoutService.retry(
            operator,
            payout.getId(),
            new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                "재시도", payout.getRevision()),
            null);

    assertThat(result.status()).isEqualTo("CONFIRMED");
    assertThat(result.failureCode()).isNull();
  }

  @Test
  @DisplayName("retry: expectedRevision이 다르면 매뉴얼 체크에서 바로 409다(DB 경합 없이)")
  void retryStaleExpectedRevisionConflicts() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    baseRewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                        "재시도", payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 retry를 재전송하면 두 번째 요청은 다시 처리하지 않고 같은 결과를 돌려준다")
  void retryIsIdempotent() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    baseRewardPayoutRepository.saveAndFlush(payout);
    String key = "idem-retry-" + UUID.randomUUID();
    var request =
        new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
            "재시도", payout.getRevision());

    var first = payoutService.retry(operator, payout.getId(), request, key);
    var replay = payoutService.retry(operator, payout.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("retry: TOP_LIKE의 expectedRevision이 다르면 409다")
  void retryTopLikeStaleRevisionConflicts() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    rewardPayoutRepository.saveAndFlush(payout);

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                        "재시도", payout.getRevision() + 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("retry: TOP_LIKE가 FAILED가 아니면 409다")
  void retryTopLikeRejectsNonFailedStatus() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                        null, payout.getRevision()),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.invalid_state");
  }

  @Test
  @DisplayName("retry: 매뉴얼 체크 통과 후 flush 시점에 TOP_LIKE revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void retryTopLikeFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    RewardPayout payout =
        rewardPayoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(1L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    rewardPayoutRepository.saveAndFlush(payout);
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery("UPDATE reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                        "재시도", revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
  }

  @Test
  @DisplayName("retry: 매뉴얼 체크 통과 후 flush 시점에 BASE revision이 이미 올라갔다면 409다(진짜 낙관적 락 충돌)")
  void retryBaseFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = participation();
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    payout.fail("BANK_ERROR");
    baseRewardPayoutRepository.saveAndFlush(payout);
    Long revisionSeenByCaller = payout.getRevision();
    entityManager
        .createNativeQuery(
            "UPDATE base_reward_payout SET revision = revision + 1 WHERE payout_id = :id")
        .setParameter("id", payout.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                payoutService.retry(
                    operator,
                    payout.getId(),
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto(
                        "재시도", revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.reward.payout.stale_revision");
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
