package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.SlotReassignReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSettlementReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminRoundConsoleServiceTest extends AbstractContainerTest {

  @Autowired private AdminRoundConsoleService consoleService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventAuthTargetRepository authTargetRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventSettlementReportRepository settlementReportRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserCouponRepository userCouponRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private ZoneEventType type;

  @BeforeEach
  void setUp() {
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    operator =
        new AuthenticatedUser(
            savedUser().getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    normalUser = AuthenticatedUser.from(savedUser());
  }

  @Test
  @DisplayName("회차를 생성하면 구역마다 AUTH 슬롯이 붙고 감사 로그가 남는다")
  void createRound() {
    AdminRoundResDto round =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                RoundType.REGULAR,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1),
                "Asia/Seoul",
                List.of("SUYEONG_NAMGU", "HAEUNDAE_GIJANG")));

    assertThat(round.status()).isEqualTo(RoundStatus.SCHEDULED);
    assertThat(round.slots()).hasSize(2);
    assertThat(round.slots()).allMatch(s -> "AUTH".equals(s.slotKind()));
    assertThat(
            auditLogRepository.findByTargetTypeAndTargetId(
                "ROUND", UUID.fromString(round.roundId())))
        .anyMatch(a -> a.getAction().equals("CREATE_ROUND"));
  }

  @Test
  @DisplayName("운영자가 아니면 회차 생성·조회는 403이다")
  void forbidden() {
    RoundCreateReqDto req =
        new RoundCreateReqDto(
            null, OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null, List.of("YEONGDO"));
    assertThatThrownBy(() -> consoleService.createRound(normalUser, req))
        .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> consoleService.suggestSlots(normalUser, 6))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("잘못된 구역으로 회차를 만들면 400이다")
  void invalidZone() {
    RoundCreateReqDto req =
        new RoundCreateReqDto(
            null, OffsetDateTime.now(), OffsetDateTime.now().plusDays(1), null, List.of("NOPE"));
    assertThatThrownBy(() -> consoleService.createRound(operator, req))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("캘린더 조회·상세 조회·슬롯 배정 제안이 동작한다")
  void listSuggestDetail() {
    AdminRoundResDto created =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1),
                null,
                List.of("YEONGDO")));

    assertThat(
            consoleService.listRounds(
                operator, OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1)))
        .hasSize(1);
    assertThat(consoleService.roundDetail(operator, UUID.fromString(created.roundId())).roundId())
        .isEqualTo(created.roundId());
    assertThat(consoleService.suggestSlots(operator, 6).slots()).hasSize(6);
    assertThatThrownBy(() -> consoleService.roundDetail(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("이벤트가 없는 슬롯은 구역을 교체할 수 있고, 배정되면 막힌다")
  void reassignSlot() {
    AdminRoundResDto created =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1),
                null,
                List.of("YEONGDO")));
    UUID roundId = UUID.fromString(created.roundId());
    UUID slotId = UUID.fromString(created.slots().get(0).slotId());

    AdminRoundResDto after =
        consoleService.reassignSlot(
            operator, roundId, new SlotReassignReqDto(slotId, "WESTERN_BUSAN"));
    assertThat(after.slots().get(0).zoneId()).isEqualTo("WESTERN_BUSAN");

    slotRepository.findById(slotId).orElseThrow().assignEvent(UUID.randomUUID());
    assertThatThrownBy(
            () ->
                consoleService.reassignSlot(
                    operator, roundId, new SlotReassignReqDto(slotId, "OLD_DOWNTOWN")))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("예비 타겟을 등록하고 이벤트 인증 타겟을 우천 교체한다")
  void backupAndSwapTarget() {
    ZoneEventRound round = round(RoundStatus.OPEN);
    ZoneEvent event = event(round.getId(), ZoneEventStatus.ACTIVE);
    ZoneEventAuthTarget original = authTarget(event, 35.1, 129.1);

    consoleService.addBackupTarget(
        operator,
        round.getId(),
        new BackupTargetReqDto(
            ZoneEventTargetKind.PLACE, null, "실내 대체지", "안내", null, 35.2, 129.2, 80));
    UUID backupId =
        consoleService.roundDetail(operator, round.getId()).backups().get(0).targetId() != null
            ? UUID.fromString(
                consoleService.roundDetail(operator, round.getId()).backups().get(0).targetId())
            : null;

    consoleService.swapTarget(
        operator, round.getId(), new SwapTargetReqDto(event.getId(), backupId));

    ZoneEventAuthTarget swapped = authTargetRepository.findById(original.getId()).orElseThrow();
    assertThat(swapped.getLatitude()).isEqualTo(35.2);
    assertThat(swapped.getRadiusM()).isEqualTo(80);
  }

  @Test
  @DisplayName("수동 오픈·종료는 슬롯 이벤트를 함께 전환하고 멱등이다")
  void openClose() {
    ZoneEventRound round = round(RoundStatus.SCHEDULED);
    ZoneEvent event = event(round.getId(), ZoneEventStatus.SCHEDULED);
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId("SUYEONG_NAMGU")
            .eventId(event.getId())
            .build());

    consoleService.open(operator, round.getId());
    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.OPEN);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
    consoleService.open(operator, round.getId()); // 멱등

    consoleService.close(operator, round.getId());
    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.CLOSED);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);
    consoleService.close(operator, round.getId()); // 멱등
  }

  @Test
  @DisplayName("이벤트 미배정 슬롯만 있는 회차도 오픈된다(전환할 이벤트 없음)")
  void openRoundWithUnassignedSlots() {
    AdminRoundResDto created =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1),
                null,
                List.of("YEONGDO")));
    UUID roundId = UUID.fromString(created.roundId());

    consoleService.open(operator, roundId);

    assertThat(roundRepository.findById(roundId).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.OPEN);
  }

  @Test
  @DisplayName("슬롯 이벤트가 이미 대상 상태면 건너뛰고 회차만 전환된다")
  void openSkipsAlreadyTransitionedEvent() {
    ZoneEventRound round = round(RoundStatus.SCHEDULED);
    ZoneEvent event = event(round.getId(), ZoneEventStatus.ACTIVE); // from(SCHEDULED)과 불일치
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId("SUYEONG_NAMGU")
            .eventId(event.getId())
            .build());

    consoleService.open(operator, round.getId());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.OPEN);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("정산은 미완료 참여를 만료하고 TOP_LIKE 보상을 지급하며 리포트를 저장한다(멱등)")
  void settle() {
    ZoneEventRound round = round(RoundStatus.CLOSED);
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.COUPON)
            .code("COUPON_CAFE")
            .name("카페 쿠폰")
            .stock(5)
            .validDays(30)
            .build());
    ZoneEvent event = eventWithExcellence(round.getId());
    ZoneEventParticipation winner = success(event, 10);
    ZoneEventParticipation joined = joined(event);

    @SuppressWarnings("unchecked")
    Map<String, Object> report = consoleService.settle(operator, round.getId());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.SETTLED);
    assertThat(participationRepository.findById(joined.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.CANCELLED);
    assertThat(userCouponRepository.findAll()).hasSize(1);
    assertThat(settlementReportRepository.findById(round.getId())).isPresent();
    List<?> events = (List<?>) report.get("events");
    assertThat(events).hasSize(1);
    assertThat(winner.getLikeCount()).isEqualTo(10);

    Map<String, Object> again = consoleService.settle(operator, round.getId());
    assertThat(userCouponRepository.findAll()).hasSize(1); // 멱등: 중복 지급 없음
    assertThat(again.get("roundId")).isEqualTo(round.getId().toString());
  }

  @Test
  @DisplayName("정산 리포트가 없으면 404, 있으면 저장된 리포트를 돌려준다")
  void settlementReport() {
    ZoneEventRound round = round(RoundStatus.CLOSED);
    assertThatThrownBy(() -> consoleService.settlementReport(operator, round.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    consoleService.settle(operator, round.getId());
    assertThat(consoleService.settlementReport(operator, round.getId()).get("roundId"))
        .isEqualTo(round.getId().toString());
  }

  private ZoneEventRound round(RoundStatus status) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .startsAt(OffsetDateTime.now().minusHours(1))
            .endsAt(OffsetDateTime.now().plusHours(1))
            .status(status)
            .build());
  }

  private ZoneEvent event(UUID roundId, ZoneEventStatus status) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .roundId(roundId)
            .title("이벤트")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(120)
            .status(status)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEvent eventWithExcellence(UUID roundId) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .roundId(roundId)
            .title("이벤트")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(120)
            .status(ZoneEventStatus.CLOSED)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .excellenceReward(new RewardSnapshot(null, null, 1, "COUPON_CAFE"))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEventAuthTarget authTarget(ZoneEvent event, double lat, double lng) {
    return authTargetRepository.save(
        ZoneEventAuthTarget.builder()
            .event(event)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("원래 장소")
            .latitude(lat)
            .longitude(lng)
            .radiusM(100)
            .build());
  }

  private ZoneEventParticipation success(ZoneEvent event, long likeCount) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser().getId())
            .status(ParticipationStatus.JOINED)
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

  private ZoneEventParticipation joined(ZoneEvent event) {
    return participationRepository.save(
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser().getId())
            .status(ParticipationStatus.JOINED)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build());
  }

  private User savedUser() {
    return userRepository.save(
        User.builder()
            .email("u-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname("tester")
            .role(UserRole.USER)
            .build());
  }
}
