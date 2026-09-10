package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminParticipationPageResDto;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminReviewServiceTest extends AbstractContainerTest {

  @Autowired private AdminReviewService reviewService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private RewardService rewardService;
  @Autowired private UserRepository userRepository;
  @Autowired private RewardPayoutRepository payoutRepository;
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
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_BASE")
            .name("포인트")
            .pointAmount(50)
            .build());
    operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("회수하면 REVOKED가 되고 지급 포인트가 되돌아간다")
  void revokeReversesReward() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    rewardService.grantBaseReward(p.getUserId(), p.getId(), event.getId(), 50, null);
    assertThat(userPointService.getBalance(p.getUserId())).isEqualTo(50);

    reviewService.revoke(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.REVOKED);
    assertThat(userPointService.getBalance(p.getUserId())).isZero();
  }

  @Test
  @DisplayName("숨김 해제하면 hidden이 풀리고 신고가 DISMISSED된다")
  void unhideDismissesReports() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, true));
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());

    reviewService.unhide(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getHidden()).isFalse();
    assertThat(reportRepository.findByParticipationId(p.getId()).get(0).getStatus())
        .isEqualTo(ReportStatus.DISMISSED);
  }

  @Test
  @DisplayName("숨김 해제 시 HELD_REPORT였던 지급 건은 보류가 풀린다")
  void unhideReleasesPayoutHold() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, true));
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());
    RewardPayout payout =
        payoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(5L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());
    payout.hold();

    reviewService.unhide(operator, p.getId());

    assertThat(payoutRepository.findById(payout.getId()).orElseThrow().getHoldStatus())
        .isEqualTo(PayoutHoldStatus.NONE);
  }

  @Test
  @DisplayName("회수 시 아직 발송 전인 지급 건은 FAILED로 바뀐다")
  void revokeFailsUnsentPayout() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    RewardPayout payout =
        payoutRepository.save(
            RewardPayout.builder()
                .eventId(event.getId())
                .participationId(p.getId())
                .rankN(1)
                .likeCountAtClose(5L)
                .reward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .build());

    reviewService.revoke(operator, p.getId());

    RewardPayout reloaded = payoutRepository.findById(payout.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(RewardPayoutStatus.FAILED);
    assertThat(reloaded.getFailureCode()).isEqualTo("PARTICIPATION_REVOKED");
  }

  @Test
  @DisplayName("회수 시 아직 지급 전인 BASE 지급 건도 FAILED로 바뀐다")
  void revokeFailsUnpaidBasePayout() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    baseRewardPayoutRepository.saveAndFlush(payout);

    reviewService.revoke(operator, p.getId());

    BaseRewardPayout reloaded = baseRewardPayoutRepository.findById(payout.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BaseRewardPayoutStatus.FAILED);
    assertThat(reloaded.getFailureCode()).isEqualTo("PARTICIPATION_REVOKED");
  }

  @Test
  @DisplayName("회수해도 이미 PAID인 BASE 지급 건은 건드리지 않는다")
  void revokeLeavesPaidBasePayoutUntouched() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    BaseRewardPayout payout =
        baseRewardPayoutRepository.save(
            BaseRewardPayout.builder()
                .participationId(p.getId())
                .reward(new RewardSnapshot(50, null, null, null))
                .build());
    payout.confirm(operator.id());
    payout.markSent(OffsetDateTime.now(), null);
    baseRewardPayoutRepository.saveAndFlush(payout);

    reviewService.revoke(operator, p.getId());

    BaseRewardPayout reloaded = baseRewardPayoutRepository.findById(payout.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BaseRewardPayoutStatus.PAID);
    assertThat(reloaded.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("상태가 맞지 않으면 회수는 409다")
  void invalidStateTransitions() {
    ZoneEventParticipation joined =
        participationRepository.save(participation(ParticipationStatus.JOINED, false));
    assertThatThrownBy(() -> reviewService.revoke(operator, joined.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("없는 참여 회수·숨김해제는 404다")
  void notFound() {
    assertThatThrownBy(() -> reviewService.revoke(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> reviewService.unhide(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("전체 참여 목록은 roundId·eventId·zoneId·userId·status·keyword로 필터링하고 페이지 정보를 돌려준다")
  void listFiltersAndPages() {
    ZoneEventParticipation match =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    participationRepository.save(participation(ParticipationStatus.FAIL, false)); // status 안 맞음

    AdminParticipationPageResDto page =
        reviewService.list(
            operator, null, event.getId(), event.getZoneId(), null, "SUCCESS", null, 1, 20);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).participationId()).isEqualTo(match.getId().toString());
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  @DisplayName("keyword는 참여자 닉네임·이메일로 찾는다")
  void listFiltersByKeyword() {
    User target = savedUser("특이닉네임");
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(target.getId())
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    participationRepository.save(p);
    participationRepository.save(participation(ParticipationStatus.SUCCESS, false));

    AdminParticipationPageResDto page =
        reviewService.list(operator, null, null, null, null, null, "특이닉네임", 1, 20);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).userId()).isEqualTo(target.getId().toString());
  }

  @Test
  @DisplayName("keyword에 일치하는 사용자가 없으면 빈 페이지를 돌려준다")
  void listWithKeywordNoMatchReturnsEmptyPage() {
    participationRepository.save(participation(ParticipationStatus.SUCCESS, false));

    AdminParticipationPageResDto page =
        reviewService.list(operator, null, null, null, null, null, "존재하지않는닉네임", 1, 20);

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  @DisplayName("roundId·userId로도 필터링한다")
  void listFiltersByRoundIdAndUserId() {
    UUID roundId = UUID.randomUUID();
    ZoneEvent roundEvent =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(event.getType())
                .roundId(roundId)
                .title("회차 이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    User target = savedUser("roundUser");
    ZoneEventParticipation match =
        ZoneEventParticipation.builder()
            .event(roundEvent)
            .userId(target.getId())
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    participationRepository.save(match);
    participationRepository.save(participation(ParticipationStatus.SUCCESS, false)); // 다른 이벤트·사용자

    AdminParticipationPageResDto page =
        reviewService.list(operator, roundId, null, null, target.getId(), null, null, 1, 20);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).participationId()).isEqualTo(match.getId().toString());
  }

  @Test
  @DisplayName("운영자가 아니면 목록 조회는 403이다")
  void listForbidden() {
    AuthenticatedUser normalUser = AuthenticatedUser.from(savedUser("normal"));
    assertThatThrownBy(
            () -> reviewService.list(normalUser, null, null, null, null, null, null, 1, 20))
        .isInstanceOf(com.butingbe.global.error.exception.ForbiddenException.class);
  }

  private ZoneEventParticipation participation(ParticipationStatus status, boolean hidden) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser("p").getId())
            .status(status)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    if (hidden) {
      ReflectionTestUtils.setField(p, "hidden", true);
    }
    if (status == ParticipationStatus.SUCCESS) {
      ReflectionTestUtils.setField(p, "completedAt", OffsetDateTime.now());
    }
    return p;
  }

  private User savedUser(String nick) {
    return userRepository.save(
        User.builder()
            .email(nick + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nick)
            .role(UserRole.USER)
            .build());
  }
}
