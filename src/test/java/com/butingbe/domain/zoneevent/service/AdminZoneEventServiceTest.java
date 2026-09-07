package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventUpdateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AuthTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RewardSnapshotReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventService adminZoneEventService;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private ZoneEventType type;

  @BeforeEach
  void setUp() {
    zoneEventTypeRepository.save(
        ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("MISSION").name("미션").requiresUpload(false).build());
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.BADGE)
            .code("SPOT_GWANGAN_BRIDGE")
            .name("광안대교 스팟")
            .build());
    operator = AuthenticatedUser.from(savedUser("admin", UserRole.ADMIN));
    normalUser = AuthenticatedUser.from(savedUser("user", UserRole.USER));
  }

  private ZoneEventRound draftRound(int roundNo) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(roundNo)
            .startsAt(OffsetDateTime.now())
            .endsAt(OffsetDateTime.now().plusDays(1))
            .excellenceReward(new RewardSnapshot(null, null, 3, "COUPON_CAFE"))
            .build());
  }

  @Test
  @DisplayName("회차에 슬롯을 만들면 slotCode가 {roundNo}-A부터 순서대로 발급되고 슬롯이 연결된다")
  void createAssignsSlotCodeAndLinksSlot() {
    ZoneEventRound round = draftRound(11);

    AdminZoneEventResDto first = adminZoneEventService.create(operator, createReq(round.getId(), "YEONGDO"));
    AdminZoneEventResDto second =
        adminZoneEventService.create(operator, createReq(round.getId(), "OLD_DOWNTOWN"));

    assertThat(first.slotCode()).isEqualTo("11-A");
    assertThat(second.slotCode()).isEqualTo("11-B");
    assertThat(slotRepository.findByRound_IdAndZoneId(round.getId(), "YEONGDO"))
        .isPresent()
        .get()
        .extracting(s -> s.getEventId().toString())
        .isEqualTo(first.eventId());
  }

  @Test
  @DisplayName("같은 회차에 같은 구역을 두 번 넣으면 409")
  void duplicateZoneInRoundConflicts() {
    ZoneEventRound round = draftRound(12);
    adminZoneEventService.create(operator, createReq(round.getId(), "YEONGDO"));

    // 시간대를 충분히 띄워서(10일 뒤) 겹침 검증이 아니라 "회차 내 중복 구역" 검증에서 막히는지 검증한다.
    assertThatThrownBy(
            () ->
                adminZoneEventService.create(
                    operator,
                    createReq(round.getId(), "YEONGDO", OffsetDateTime.now().plusDays(10))))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("한 회차에 5번째 구역을 넣으면 409")
  void fifthZoneInRoundConflicts() {
    ZoneEventRound round = draftRound(13);
    adminZoneEventService.create(operator, createReq(round.getId(), "YEONGDO"));
    adminZoneEventService.create(operator, createReq(round.getId(), "OLD_DOWNTOWN"));
    adminZoneEventService.create(operator, createReq(round.getId(), "SUYEONG_NAMGU"));
    adminZoneEventService.create(operator, createReq(round.getId(), "WESTERN_BUSAN"));

    assertThatThrownBy(
            () -> adminZoneEventService.create(operator, createReq(round.getId(), "CENTRAL_NORTH")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("같은 구역·겹치는 시간대에 SCHEDULED/ACTIVE 이벤트가 있으면 409")
  void overlappingZoneTimeConflicts() {
    OffsetDateTime start = OffsetDateTime.now().plusDays(5);
    adminZoneEventService.create(
        operator,
        new AdminZoneEventCreateReqDto(
            "YEONGDO", type.getTypeCode(), "1차", null, start, 120, null, 1,
            new RewardSnapshotReqDto(50, null, null, null), null, null));

    AdminZoneEventCreateReqDto overlapping =
        new AdminZoneEventCreateReqDto(
            "YEONGDO", type.getTypeCode(), "2차", null, start.plusMinutes(60), 120, null, 1,
            new RewardSnapshotReqDto(50, null, null, null), null, null);

    assertThatThrownBy(() -> adminZoneEventService.create(operator, overlapping))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("excellenceReward를 안 넘기면 회차 기본값을 물려받는다")
  void inheritsRoundExcellenceReward() {
    ZoneEventRound round = draftRound(14);

    AdminZoneEventResDto created =
        adminZoneEventService.create(operator, createReq(round.getId(), "YEONGDO"));

    assertThat(created.excellenceReward().topN()).isEqualTo(3);
    assertThat(created.excellenceReward().prizeRewardCode()).isEqualTo("COUPON_CAFE");
  }

  @Test
  @DisplayName("expectedRevision이 다르면 수정 시 409")
  void updateWithStaleRevisionConflicts() {
    ZoneEventRound round = draftRound(15);
    AdminZoneEventResDto created =
        adminZoneEventService.create(operator, createReq(round.getId(), "YEONGDO"));

    AdminZoneEventUpdateReqDto staleUpdate =
        new AdminZoneEventUpdateReqDto(
            "새 제목", null, null, null, null, null, null, null, null, null, "사유",
            created.revision() + 1);

    assertThatThrownBy(
            () -> adminZoneEventService.update(operator, UUID.fromString(created.eventId()), staleUpdate))
        .isInstanceOf(ConflictException.class);
  }

  private AdminZoneEventCreateReqDto createReq(UUID roundId, String zoneId) {
    return createReq(roundId, zoneId, OffsetDateTime.now().plusDays(1));
  }

  private AdminZoneEventCreateReqDto createReq(
      UUID roundId, String zoneId, OffsetDateTime startsAt) {
    return new AdminZoneEventCreateReqDto(
        zoneId, type.getTypeCode(), "미션", null, startsAt, 120, roundId, 1,
        new RewardSnapshotReqDto(50, null, null, null), null, null);
  }

  @Test
  @DisplayName("운영자가 인증 이벤트를 생성하면 SCHEDULED 상태로 타겟과 함께 저장된다")
  void createEvent() {
    AdminZoneEventResDto created = adminZoneEventService.create(operator, createRequest());

    assertThat(created.status()).isEqualTo("SCHEDULED");
    assertThat(created.zoneId()).isEqualTo("SUYEONG_NAMGU");
    assertThat(created.authTarget().radiusM()).isEqualTo(100);
    assertThat(created.baseReward().badgeCode()).isEqualTo("SPOT_GWANGAN_BRIDGE");
    assertThat(created.joinedCount()).isZero();
  }

  @Test
  @DisplayName("운영자가 아니면 403이다")
  void nonOperatorForbidden() {
    assertThatThrownBy(() -> adminZoneEventService.create(normalUser, createRequest()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("인증 타입인데 타겟이 없으면 400이다")
  void authEventWithoutTargetRejected() {
    AdminZoneEventCreateReqDto request =
        new AdminZoneEventCreateReqDto(
            "SUYEONG_NAMGU",
            "PLACE_AUTH",
            "제목",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, null, null, null),
            null,
            null);
    assertThatThrownBy(() -> adminZoneEventService.create(operator, request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("존재하지 않는 배지 코드는 400이다")
  void unknownBadgeCodeRejected() {
    AdminZoneEventCreateReqDto request =
        new AdminZoneEventCreateReqDto(
            "SUYEONG_NAMGU",
            "PLACE_AUTH",
            "제목",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, "NOPE_BADGE", null, null),
            null,
            target());
    assertThatThrownBy(() -> adminZoneEventService.create(operator, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.reward.catalog_not_found");
  }

  @Test
  @DisplayName("존재하지 않는 타입은 400이다")
  void unknownTypeRejected() {
    AdminZoneEventCreateReqDto request =
        new AdminZoneEventCreateReqDto(
            "SUYEONG_NAMGU",
            "GHOST_TYPE",
            "제목",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, null, null, null),
            null,
            target());
    assertThatThrownBy(() -> adminZoneEventService.create(operator, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.type_not_found");
  }

  @Test
  @DisplayName("활성화·종료 상태 전이와 잘못된 전이(409)를 처리한다")
  void stateTransitions() {
    UUID eventId =
        UUID.fromString(adminZoneEventService.create(operator, createRequest()).eventId());

    assertThat(adminZoneEventService.activate(operator, eventId).status()).isEqualTo("ACTIVE");
    // 이미 ACTIVE인데 다시 activate → 409
    assertThatThrownBy(() -> adminZoneEventService.activate(operator, eventId))
        .isInstanceOf(ConflictException.class);
    assertThat(adminZoneEventService.close(operator, eventId).status()).isEqualTo("CLOSED");
  }

  @Test
  @DisplayName("취소 시 열린 참여는 EVENT_CANCELLED로 정리되고 성공 참여는 유지된다(BR-13)")
  void cancelClosesOpenParticipations() {
    UUID eventId =
        UUID.fromString(adminZoneEventService.create(operator, createRequest()).eventId());
    adminZoneEventService.activate(operator, eventId);
    UUID joinerId = savedUser("joiner", UserRole.USER).getId();
    UUID winnerId = savedUser("winner", UserRole.USER).getId();
    ZoneEventParticipation open = saveParticipation(eventId, joinerId, ParticipationStatus.JOINED);
    ZoneEventParticipation success =
        saveParticipation(eventId, winnerId, ParticipationStatus.SUCCESS);

    adminZoneEventService.cancel(operator, eventId);

    assertThat(participationRepository.findById(open.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.CANCELLED);
    assertThat(participationRepository.findById(open.getId()).orElseThrow().getCancelReason())
        .isEqualTo("EVENT_CANCELLED");
    assertThat(participationRepository.findById(success.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.SUCCESS);
  }

  @Test
  @DisplayName("ACTIVE 이벤트는 제목은 바꿀 수 있지만 구역 변경은 409다")
  void updateRestrictionsOnActive() {
    AdminZoneEventResDto created = adminZoneEventService.create(operator, createRequest());
    UUID eventId = UUID.fromString(created.eventId());
    AdminZoneEventResDto activated = adminZoneEventService.activate(operator, eventId);

    AdminZoneEventResDto updated =
        adminZoneEventService.update(
            operator,
            eventId,
            new AdminZoneEventUpdateReqDto(
                "새 제목", null, null, null, null, null, null, null, null, null, null,
                activated.revision()));
    assertThat(updated.title()).isEqualTo("새 제목");

    assertThatThrownBy(
            () ->
                adminZoneEventService.update(
                    operator,
                    eventId,
                    new AdminZoneEventUpdateReqDto(
                        null, null, null, null, null, "YEONGDO", null, null, null, null, null,
                        updated.revision())))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("SCHEDULED 이벤트는 구역·타겟을 수정할 수 있다")
  void updateScheduledFieldsAndTarget() {
    AdminZoneEventResDto created = adminZoneEventService.create(operator, createRequest());
    UUID eventId = UUID.fromString(created.eventId());

    AdminZoneEventResDto updated =
        adminZoneEventService.update(
            operator,
            eventId,
            new AdminZoneEventUpdateReqDto(
                null,
                null,
                null,
                null,
                null,
                "YEONGDO",
                null,
                null,
                null,
                new AdminZoneEventUpdateReqDto.AuthTargetPatchReqDto(
                    null, "새 가이드", null, null, null, 200),
                null,
                created.revision()));

    assertThat(updated.zoneId()).isEqualTo("YEONGDO");
    assertThat(updated.authTarget().radiusM()).isEqualTo(200);
    assertThat(updated.authTarget().guideText()).isEqualTo("새 가이드");
  }

  @Test
  @DisplayName("목록을 구역·상태 필터와 커서 페이징으로 조회한다")
  void listWithFiltersAndCursor() {
    for (int i = 0; i < 3; i++) {
      adminZoneEventService.create(operator, createRequest(OffsetDateTime.now().plusDays(2L * i)));
    }

    AdminZoneEventPageResDto first =
        adminZoneEventService.list(operator, "SUYEONG_NAMGU", "SCHEDULED", null, null, null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();

    AdminZoneEventPageResDto second =
        adminZoneEventService.list(
            operator, "SUYEONG_NAMGU", "SCHEDULED", null, null, first.nextCursor(), 2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.hasNext()).isFalse();
  }

  @Test
  @DisplayName("상세는 참여·성공 수를 함께 준다")
  void detailWithStats() {
    UUID eventId =
        UUID.fromString(adminZoneEventService.create(operator, createRequest()).eventId());
    saveParticipation(eventId, savedUser("p1", UserRole.USER).getId(), ParticipationStatus.SUCCESS);
    saveParticipation(eventId, savedUser("p2", UserRole.USER).getId(), ParticipationStatus.JOINED);

    AdminZoneEventResDto detail = adminZoneEventService.detail(operator, eventId);

    assertThat(detail.joinedCount()).isEqualTo(2);
    assertThat(detail.successCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("업로드가 필요 없는 타입은 타겟 없이도 생성되고, 있으면 저장한다")
  void createNonUploadType() {
    zoneEventTypeRepository.save(
        ZoneEventType.builder().typeCode("MUKJJIPPA").name("묵찌빠").requiresUpload(false).build());
    AdminZoneEventCreateReqDto withTarget =
        new AdminZoneEventCreateReqDto(
            "SUYEONG_NAMGU",
            "MUKJJIPPA",
            "묵찌빠",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, null, null, null),
            null,
            target());

    AdminZoneEventResDto created = adminZoneEventService.create(operator, withTarget);
    assertThat(created.authTarget()).isNotNull();
  }

  @Test
  @DisplayName("모든 수정 필드와 타겟 좌표까지 반영한다")
  void updateAllFields() {
    AdminZoneEventResDto created = adminZoneEventService.create(operator, createRequest());
    UUID eventId = UUID.fromString(created.eventId());

    AdminZoneEventResDto updated =
        adminZoneEventService.update(
            operator,
            eventId,
            new AdminZoneEventUpdateReqDto(
                "새 제목",
                "새 설명",
                720,
                2,
                new RewardSnapshotReqDto(null, null, 10, null),
                "YEONGDO",
                "PLACE_AUTH",
                OffsetDateTime.now().plusDays(1),
                new RewardSnapshotReqDto(100, "SPOT_GWANGAN_BRIDGE", null, null),
                new AdminZoneEventUpdateReqDto.AuthTargetPatchReqDto(
                    "새 장소", "새 가이드", "uploads/new.jpg", 35.2, 129.2, 300),
                null,
                created.revision()));

    assertThat(updated.title()).isEqualTo("새 제목");
    assertThat(updated.description()).isEqualTo("새 설명");
    assertThat(updated.durationMinutes()).isEqualTo(720);
    assertThat(updated.successLimitPerUser()).isEqualTo(2);
    assertThat(updated.baseReward().points()).isEqualTo(100);
    assertThat(updated.excellenceReward().topN()).isEqualTo(10);
    assertThat(updated.authTarget().placeName()).isEqualTo("새 장소");
    assertThat(updated.authTarget().latitude()).isEqualTo(35.2);
    assertThat(updated.authTarget().longitude()).isEqualTo(129.2);
  }

  @Test
  @DisplayName("목록 기본 크기·잘못된 상태·잘못된 커서를 처리한다")
  void listEdgeCases() {
    adminZoneEventService.create(operator, createRequest());

    assertThat(adminZoneEventService.list(operator, null, null, null, null, null, null).items())
        .isNotEmpty();
    assertThatThrownBy(
            () -> adminZoneEventService.list(operator, null, "GHOST", null, null, null, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> adminZoneEventService.list(operator, null, null, null, null, "!!bad!!", 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("잘못된 우수 보상 코드·구역·타겟 종류는 400이다")
  void createValidationBranches() {
    AdminZoneEventCreateReqDto badPrize =
        new AdminZoneEventCreateReqDto(
            "SUYEONG_NAMGU",
            "PLACE_AUTH",
            "제목",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, null, null, null),
            new RewardSnapshotReqDto(null, null, 5, "NOPE_PRIZE"),
            target());
    assertThatThrownBy(() -> adminZoneEventService.create(operator, badPrize))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.reward.catalog_not_found");

    AdminZoneEventCreateReqDto badZone =
        new AdminZoneEventCreateReqDto(
            "NOWHERE",
            "PLACE_AUTH",
            "제목",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, null, null, null),
            null,
            target());
    assertThatThrownBy(() -> adminZoneEventService.create(operator, badZone))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.invalid_zone");

    AdminZoneEventCreateReqDto badKind =
        new AdminZoneEventCreateReqDto(
            "SUYEONG_NAMGU",
            "PLACE_AUTH",
            "제목",
            null,
            OffsetDateTime.now(),
            1440,
            null,
            1,
            new RewardSnapshotReqDto(50, null, null, null),
            null,
            new AuthTargetReqDto("GHOST", null, "광안", null, null, 35.1, 129.1, 100));
    assertThatThrownBy(() -> adminZoneEventService.create(operator, badKind))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("기간 필터와 형식 오류 커서를 처리한다")
  void listPeriodAndMalformedCursor() {
    adminZoneEventService.create(operator, createRequest());

    assertThat(
            adminZoneEventService
                .list(
                    operator,
                    null,
                    null,
                    OffsetDateTime.now().minusDays(1),
                    OffsetDateTime.now().plusDays(1),
                    null,
                    20)
                .items())
        .isNotEmpty();

    String noPipe =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("nopipe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(
            () -> adminZoneEventService.list(operator, null, null, null, null, noPipe, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("종료된 이벤트는 취소할 수 없다(409)")
  void cancelClosedEventRejected() {
    UUID eventId =
        UUID.fromString(adminZoneEventService.create(operator, createRequest()).eventId());
    adminZoneEventService.activate(operator, eventId);
    adminZoneEventService.close(operator, eventId);

    assertThatThrownBy(() -> adminZoneEventService.cancel(operator, eventId))
        .isInstanceOf(ConflictException.class);
  }

  private AdminZoneEventCreateReqDto createRequest() {
    return createRequest(OffsetDateTime.now());
  }

  private AdminZoneEventCreateReqDto createRequest(OffsetDateTime startsAt) {
    return new AdminZoneEventCreateReqDto(
        "SUYEONG_NAMGU",
        "PLACE_AUTH",
        "광안대교 야경 담기",
        "야경 촬영",
        startsAt,
        1440,
        null,
        1,
        new RewardSnapshotReqDto(50, "SPOT_GWANGAN_BRIDGE", null, null),
        new RewardSnapshotReqDto(null, null, 5, null),
        target());
  }

  private AuthTargetReqDto target() {
    return new AuthTargetReqDto(
        "PLACE", "gwangan-bridge", "광안대교 야경", "가이드", null, 35.153, 129.118, 100);
  }

  private ZoneEventParticipation saveParticipation(
      UUID eventId, UUID userId, ParticipationStatus status) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(zoneEventRef(eventId))
            .userId(userId)
            .status(status)
            .gpsLat(35.15)
            .gpsLng(129.11)
            .joinedAt(OffsetDateTime.now())
            .build();
    return participationRepository.save(p);
  }

  @Autowired
  private com.butingbe.domain.zoneevent.repository.ZoneEventRepository zoneEventRepository;

  private com.butingbe.domain.zoneevent.entity.ZoneEvent zoneEventRef(UUID eventId) {
    return zoneEventRepository.findById(eventId).orElseThrow();
  }

  private User savedUser(String nickname, UserRole role) {
    return userRepository.save(
        User.builder()
            .email(nickname + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nickname)
            .role(role)
            .build());
  }
}
