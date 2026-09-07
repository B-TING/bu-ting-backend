package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.reward.dto.response.SettlementReportResDto;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RewardSettlementServiceTest extends AbstractContainerTest {

  @Autowired private RewardSettlementService settlementService;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
  @Autowired private UserCouponRepository userCouponRepository;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private UserRepository userRepository;

  private ZoneEventRound round;
  private ZoneEventType type;
  private RewardCatalog coupon;

  @BeforeEach
  void setUp() {
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    round =
        roundRepository.save(
            ZoneEventRound.builder()
                .startsAt(OffsetDateTime.now().minusDays(1))
                .endsAt(OffsetDateTime.now())
                .status(RoundStatus.CLOSED)
                .build());
    coupon =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(RewardType.COUPON)
                .code("COUPON_CAFE")
                .name("카페 쿠폰")
                .stock(5)
                .validDays(30)
                .build());
  }

  @Test
  @DisplayName("좋아요 상위 N명에게 우수 보상을 지급하고 쿠폰함에 담는다")
  void grantsTopLike() {
    ZoneEvent event = savedEvent(2, "COUPON_CAFE");
    ZoneEventParticipation top = success(event, 10);
    ZoneEventParticipation second = success(event, 5);
    success(event, 1); // topN=2 밖

    SettlementReportResDto report = settlementService.settleTopLike(round.getId());

    assertThat(report.events()).hasSize(1);
    assertThat(report.events().get(0).prizes())
        .extracting(SettlementReportResDto.Prize::status)
        .containsOnly("GRANTED");
    assertThat(report.events().get(0).prizes()).hasSize(2);
    assertThat(userCouponRepository.findAll()).hasSize(2);
    // 재고 5 → 2 지급 후 3
    assertThat(rewardCatalogRepository.findById(coupon.getId()).orElseThrow().getStock())
        .isEqualTo(3);
    assertThat(top.getLikeCount()).isGreaterThan(second.getLikeCount());
  }

  @Test
  @DisplayName("재실행해도 중복 지급하지 않는다(멱등)")
  void idempotent() {
    ZoneEvent event = savedEvent(1, "COUPON_CAFE");
    success(event, 10);

    settlementService.settleTopLike(round.getId());
    SettlementReportResDto second = settlementService.settleTopLike(round.getId());

    assertThat(second.events().get(0).prizes())
        .extracting(SettlementReportResDto.Prize::status)
        .containsOnly("ALREADY_GRANTED");
    assertThat(rewardGrantRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("재고가 없으면 스킵으로 리포트한다")
  void skipsOutOfStock() {
    RewardCatalog empty =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(RewardType.COUPON)
                .code("COUPON_EMPTY")
                .name("품절 쿠폰")
                .stock(0)
                .build());
    ZoneEvent event = savedEvent(1, "COUPON_EMPTY");
    success(event, 10);

    SettlementReportResDto report = settlementService.settleTopLike(round.getId());

    assertThat(report.events().get(0).prizes().get(0).status()).isEqualTo("SKIPPED_OUT_OF_STOCK");
    assertThat(empty.getName()).isNotNull();
  }

  @Test
  @DisplayName("월 캡을 넘으면 스킵으로 리포트한다")
  void skipsMonthlyCap() {
    RewardCatalog capped =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(RewardType.COUPON)
                .code("COUPON_CAP")
                .name("월캡 쿠폰")
                .stock(100)
                .monthlyCap(1)
                .build());
    ZoneEvent event = savedEvent(2, "COUPON_CAP");
    success(event, 10);
    success(event, 5);

    SettlementReportResDto report = settlementService.settleTopLike(round.getId());

    assertThat(report.events().get(0).prizes())
        .extracting(SettlementReportResDto.Prize::status)
        .containsExactlyInAnyOrder("GRANTED", "SKIPPED_MONTHLY_CAP");
    assertThat(capped.getMonthlyCap()).isEqualTo(1);
  }

  @Test
  @DisplayName("우수 보상이 없는 이벤트는 정산에서 제외된다")
  void skipsEventWithoutExcellence() {
    ZoneEvent noExcellence =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .roundId(round.getId())
                .title("보상없음")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.CLOSED)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    success(noExcellence, 10);

    SettlementReportResDto report = settlementService.settleTopLike(round.getId());
    assertThat(report.events()).isEmpty();
  }

  @Test
  @DisplayName("우수 보상 코드가 카탈로그에 없거나 topN이 0이면 정산에서 제외된다")
  void skipsUnknownPrizeAndZeroTopN() {
    ZoneEvent unknownPrize = savedEvent(1, "NO_SUCH_CODE");
    success(unknownPrize, 10);
    ZoneEvent zeroTopN = savedEvent(0, "COUPON_CAFE");
    success(zeroTopN, 5);

    SettlementReportResDto report = settlementService.settleTopLike(round.getId());

    assertThat(report.events()).isEmpty();
  }

  private ZoneEvent savedEvent(int topN, String prizeCode) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .roundId(round.getId())
            .title("이벤트")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(1440)
            .status(ZoneEventStatus.CLOSED)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .excellenceReward(new RewardSnapshot(null, null, topN, prizeCode))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEventParticipation success(ZoneEvent event, long likeCount) {
    UUID userId = savedUser().getId();
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(userId)
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    p.submit("m.jpg", "후기", 35.1, 129.1, OffsetDateTime.now());
    p.markSuccess();
    ReflectionTestUtils.setField(p, "likeCount", likeCount);
    return participationRepository.save(p);
  }

  private User savedUser() {
    return userRepository.save(
        User.builder()
            .email("s-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname("winner")
            .role(UserRole.USER)
            .build());
  }
}
