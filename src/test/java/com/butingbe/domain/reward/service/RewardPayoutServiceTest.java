package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.response.PayoutGenerateResDto;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
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
class RewardPayoutServiceTest extends AbstractContainerTest {

  @Autowired private RewardPayoutService payoutService;
  @Autowired private RewardPayoutRepository payoutRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventRankingSnapshotRepository snapshotRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
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
                .startsAt(OffsetDateTime.now().minusDays(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.CLOSED)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .excellenceReward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .successLimitPerUser(10)
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
  @DisplayName("finalized된 수상자만 지급 후보를 생성한다")
  void generatesOnlyForFinalizedWinners() {
    ZoneEventParticipation winner = participation();
    ZoneEventParticipation notFinalized = participation();
    snapshotRepository.save(finalizedRow(winner, true));
    snapshotRepository.save(finalizedRow(notFinalized, false));

    PayoutGenerateResDto result = payoutService.generate(operator, event.getId());

    assertThat(result.created()).isEqualTo(1);
    assertThat(payoutRepository.existsByParticipationId(winner.getId())).isTrue();
    assertThat(payoutRepository.existsByParticipationId(notFinalized.getId())).isFalse();
  }

  @Test
  @DisplayName("이미 생성된 지급 건은 다시 만들지 않는다(멱등)")
  void skipsAlreadyGenerated() {
    ZoneEventParticipation winner = participation();
    snapshotRepository.save(finalizedRow(winner, true));
    payoutService.generate(operator, event.getId());

    PayoutGenerateResDto second = payoutService.generate(operator, event.getId());

    assertThat(second.created()).isZero();
    assertThat(second.alreadyExists()).isEqualTo(1);
    assertThat(payoutRepository.findByEventId(event.getId())).hasSize(1);
  }

  @Test
  @DisplayName("생성 시점에 미해결 신고가 있으면 HELD_REPORT 상태로 생성한다")
  void heldReportAtGenerationTime() {
    ZoneEventParticipation winner = participation();
    snapshotRepository.save(finalizedRow(winner, true));
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(winner.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());

    PayoutGenerateResDto result = payoutService.generate(operator, event.getId());

    assertThat(result.heldOnCreate()).isEqualTo(1);
    RewardPayout payout = payoutRepository.findByParticipationId(winner.getId()).orElseThrow();
    assertThat(payout.getHoldStatus().name()).isEqualTo("HELD_REPORT");
    assertThat(payout.getStatus()).isEqualTo(RewardPayoutStatus.PENDING_ASSIGN);
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

  private ZoneEventRankingSnapshot finalizedRow(ZoneEventParticipation p, boolean finalized) {
    ZoneEventRankingSnapshot row =
        ZoneEventRankingSnapshot.builder()
            .eventId(event.getId())
            .closedAt(OffsetDateTime.now())
            .version(1)
            .participationId(p.getId())
            .rankN(1)
            .likeCountAtClose(5L)
            .tied(false)
            .build();
    row = snapshotRepository.save(row);
    if (finalized) {
      row.markFinalized();
    }
    return row;
  }
}
