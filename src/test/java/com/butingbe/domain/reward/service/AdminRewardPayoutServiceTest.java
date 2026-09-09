package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.request.ReleaseHoldReqDto;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
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
