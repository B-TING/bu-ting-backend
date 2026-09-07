package com.butingbe.domain.zoneevent;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.domain.notification.dto.response.PushLogResDto;
import com.butingbe.domain.notification.entity.DevicePlatform;
import com.butingbe.domain.notification.entity.UserDeviceToken;
import com.butingbe.domain.notification.repository.UserDeviceTokenRepository;
import com.butingbe.domain.notification.service.NotificationService;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AuthTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.ParticipationSubmitReqDto;
import com.butingbe.domain.zoneevent.dto.request.RewardSnapshotReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventResDto;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSettlementReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
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

/**
 * 구역 이벤트 전 구간 해피패스 스모크: 회차 생성·오픈 → 참여 → 제출(AUTO 성공+보상+칭호) → 종료·정산(TOP_LIKE 쿠폰+리포트) → 운영 푸시.
 *
 * <p>도메인(zoneevent·reward·zonetitle·notification) 경계를 가로지르는 서비스 배선과 상태 전이를 한 번에 지키는 회귀 방어망이다. 외부
 * 연동은 실제 목 없이 안전한 기본 빈(LoggingPushSender)을 그대로 쓰고, 미디어·보상 카탈로그·칭호 정의는 테스트에서 시드한다.
 */
@Transactional
class ZoneEventLifecycleSmokeTest extends AbstractContainerTest {

  private static final String ZONE = "SUYEONG_NAMGU";
  private static final double LAT = 35.1;
  private static final double LNG = 129.1;

  @Autowired private com.butingbe.domain.zoneevent.service.AdminRoundConsoleService consoleService;

  @Autowired
  private com.butingbe.domain.zoneevent.service.AdminZoneEventService adminZoneEventService;

  @Autowired
  private com.butingbe.domain.zoneevent.service.ZoneEventParticipationService participationService;

  @Autowired private com.butingbe.domain.zoneevent.service.ZoneEventSubmitService submitService;
  @Autowired private NotificationService notificationService;
  @Autowired private UserPointService userPointService;

  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventSettlementReportRepository settlementReportRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserCouponRepository userCouponRepository;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private FileMetadataRepository fileMetadataRepository;
  @Autowired private UserDeviceTokenRepository deviceTokenRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser participant;

  @BeforeEach
  void setUp() {
    zoneEventTypeRepository.save(
        ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_BASE")
            .name("기본 포인트")
            .pointAmount(50)
            .build());
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.COUPON)
            .code("COUPON_CAFE")
            .name("카페 쿠폰")
            .stock(5)
            .validDays(30)
            .build());
    seedTitleDefs();
    fileMetadataRepository.save(
        FileMetadata.builder()
            .objectKey("auth/photo.jpg")
            .originalFileName("photo.jpg")
            .contentType("image/jpeg")
            .mediaType("IMAGE")
            .fileSize(1024L)
            .bucket("buting")
            .build());

    operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    participant = AuthenticatedUser.from(savedUser("player"));
    deviceTokenRepository.save(
        UserDeviceToken.builder()
            .userId(participant.id())
            .fcmToken("token-" + UUID.randomUUID())
            .platform(DevicePlatform.ANDROID)
            .build());
  }

  @Test
  @DisplayName("회차 오픈→참여→제출(성공·보상·칭호)→정산(쿠폰·리포트)→운영 푸시가 한 흐름으로 이어진다")
  void lifecycle() {
    // 1) 회차 생성 + 이벤트 생성 + 슬롯 연결
    AdminRoundResDto round =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                null,
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(2),
                null,
                List.of(ZONE)));
    UUID roundId = UUID.fromString(round.roundId());
    AdminZoneEventResDto event =
        adminZoneEventService.create(
            operator,
            new AdminZoneEventCreateReqDto(
                ZONE,
                "PLACE_AUTH",
                "수영 남구 인증",
                "설명",
                OffsetDateTime.now().minusMinutes(1),
                120,
                roundId,
                1,
                new RewardSnapshotReqDto(50, null, null, null),
                new RewardSnapshotReqDto(null, null, 1, "COUPON_CAFE"),
                new AuthTargetReqDto("PLACE", null, "부산타워", "가이드", null, LAT, LNG, 100)));
    UUID eventId = UUID.fromString(event.eventId());
    ZoneEventRoundSlot slot = slotRepository.findByRound_Id(roundId).get(0);
    slot.assignEvent(eventId);
    slotRepository.save(slot);

    // 2) 회차 오픈 → 이벤트 ACTIVE
    consoleService.open(operator, roundId);
    assertThat(zoneEventRepository.findById(eventId).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);

    // 3) 참여 시작(GPS 반경 내)
    ParticipationResDto joined = participationService.join(participant, eventId, LAT, LNG);
    UUID participationId = UUID.fromString(joined.participationId());

    // 4) 제출 → AUTO 성공 + 기본 보상 + 구역 칭호
    SubmitResultResDto submitted =
        submitService.submit(
            participant,
            eventId,
            participationId,
            new ParticipationSubmitReqDto("auth/photo.jpg", "후기", LAT, LNG, OffsetDateTime.now()));
    assertThat(submitted.pointBalance()).isEqualTo(50);
    assertThat(submitted.newlyEarnedTitles()).isNotEmpty();
    assertThat(userPointService.getBalance(participant.id())).isEqualTo(50);

    // 5) 회차 종료 → 정산(TOP_LIKE 쿠폰 + 리포트)
    consoleService.close(operator, roundId);
    consoleService.settle(operator, roundId);
    assertThat(settlementReportRepository.findById(roundId)).isPresent();
    assertThat(userCouponRepository.findAll()).hasSize(1);
    assertThat(zoneEventRepository.findById(eventId).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);

    // 6) 운영 푸시(전체 발송) → 로그 적재
    PushLogResDto push =
        notificationService.operatorPush(operator, "ALL", null, "정산 완료", "우수 인증 보상이 지급되었습니다");
    assertThat(push.pushLogId()).isNotBlank();
  }

  private void seedTitleDefs() {
    int[][] tiers = {{1, 1}, {2, 3}, {3, 7}};
    for (ChatZone zone : ChatZone.values()) {
      for (int[] tr : tiers) {
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode(zone.name() + "_T" + tr[0])
                .zoneId(zone.name())
                .tier(tr[0])
                .requiredSuccessCount(tr[1])
                .titleName(zone.name() + " T" + tr[0])
                .style("chip")
                .color("#000000")
                .build());
      }
    }
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
