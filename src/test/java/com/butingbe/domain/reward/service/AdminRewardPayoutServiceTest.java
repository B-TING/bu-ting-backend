package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.request.ReleaseHoldReqDto;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
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
                new RewardSnapshot(null, null, 1, "COUPON_TOP"), "1등 상품 확정", null, payout.getRevision()),
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
                        new RewardSnapshot(100, null, null, null), null, null, payout.getRevision()),
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
  @DisplayName("일괄 확정: PENDING_CONFIRM인 건들을 모두 CONFIRMED로 바꾼다")
  void bulkConfirmsAllPendingConfirmPayouts() {
    ZoneEventParticipation p1 = participation();
    ZoneEventParticipation p2 = participation();
    BaseRewardPayout base =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
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
                Map.of(base.getId().toString(), base.getRevision(), topLike.getId().toString(), topLike.getRevision())),
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
            BaseRewardPayout.builder().participationId(p1.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    BaseRewardPayout held =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder().participationId(p2.getId()).reward(new RewardSnapshot(50, null, null, null)).build());
    held.hold();
    baseRewardPayoutRepository.saveAndFlush(held);

    assertThatThrownBy(
            () ->
                payoutService.bulkConfirm(
                    operator,
                    new com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto(
                        List.of(ok.getId().toString(), held.getId().toString()),
                        Map.of(ok.getId().toString(), ok.getRevision(), held.getId().toString(), held.getRevision())),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.BulkPayoutConflictException.class)
        .satisfies(
            e ->
                assertThat(((com.butingbe.global.error.exception.BulkPayoutConflictException) e).getProblemPayoutIds())
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
