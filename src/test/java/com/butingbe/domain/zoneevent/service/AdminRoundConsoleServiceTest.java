package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminZoneEventCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RewardSnapshotReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCancelReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
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
import com.butingbe.global.error.exception.ConflictException;
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
  @Autowired private AdminZoneEventService adminZoneEventService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventAuthTargetRepository authTargetRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventSettlementReportRepository settlementReportRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserCouponRepository userCouponRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private ZoneEventType type;
  private static int roundNoSeq = 3000;

  @BeforeEach
  void setUp() {
    // requiresUpload=false: AdminZoneEventService.create()는 requiresUpload인 타입에 authTarget을
    // 필수로 요구한다. 이 테스트는 zoneEventReq()로 authTarget 없이 이벤트를 만든 뒤 필요할 때만 별도로
    // ACTIVE 인증 타겟을 추가하는 방식(예: scheduleFailsWithoutActiveTarget)에 의존하므로 false로 둔다.
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(false)
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
  @DisplayName("회차를 생성하면 DRAFT 상태이고 감사 로그가 남는다")
  void createRound() {
    AdminRoundResDto round =
        consoleService.createRound(
            operator,
            new RoundCreateReqDto(
                null,
                nextRoundNo(),
                "부산 바다 인증의 날",
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1),
                "Asia/Seoul",
                null));

    assertThat(round.status()).isEqualTo(RoundStatus.DRAFT);
    assertThat(round.slots()).isEmpty();
    assertThat(
            auditLogRepository.findByTargetTypeAndTargetId(
                "ROUND", UUID.fromString(round.roundId())))
        .anyMatch(a -> a.getAction().equals("CREATE_ROUND"));
  }

  @Test
  @DisplayName("회차 번호가 중복되면 409")
  void duplicateRoundNoConflicts() {
    int roundNo = nextRoundNo();
    consoleService.createRound(
        operator,
        new RoundCreateReqDto(
            null,
            roundNo,
            "1회차",
            OffsetDateTime.now(),
            OffsetDateTime.now().plusDays(1),
            null,
            null));

    assertThatThrownBy(
            () ->
                consoleService.createRound(
                    operator,
                    new RoundCreateReqDto(
                        null,
                        roundNo,
                        "중복",
                        OffsetDateTime.now(),
                        OffsetDateTime.now().plusDays(1),
                        null,
                        null)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("운영자가 아니면 생성·조회는 403이다")
  void forbidden() {
    RoundCreateReqDto req =
        new RoundCreateReqDto(
            null,
            nextRoundNo(),
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now().plusDays(1),
            null,
            null);
    assertThatThrownBy(() -> consoleService.createRound(normalUser, req))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("목록 조회는 status/from/to/keyword로 필터링되고 시작 시각이 지난 회차는 조회 중 자동 전환된다")
  void listRoundsFiltersAndSyncs() {
    int dueRoundNo = nextRoundNo();
    roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(dueRoundNo)
            .name("자동전환 대상")
            .startsAt(OffsetDateTime.now().minusMinutes(5))
            .endsAt(OffsetDateTime.now().plusHours(1))
            .status(RoundStatus.SCHEDULED)
            .build());
    createDraft();

    AdminRoundPageResDto draftOnly =
        consoleService.listRounds(operator, "DRAFT", null, null, null, 0, 20);
    assertThat(draftOnly.items()).allMatch(r -> r.status() == RoundStatus.DRAFT);
    assertThat(draftOnly.totalElements()).isEqualTo(draftOnly.items().size());
    assertThat(draftOnly.totalPages()).isGreaterThanOrEqualTo(1);

    AdminRoundPageResDto keywordMatch =
        consoleService.listRounds(operator, null, null, null, "자동전환", 0, 20);
    assertThat(keywordMatch.items()).hasSize(1);
    assertThat(keywordMatch.totalElements()).isEqualTo(1);
    assertThat(keywordMatch.totalPages()).isEqualTo(1);
    assertThat(roundRepository.findAll())
        .filteredOn(r -> r.getRoundNo().equals(dueRoundNo))
        .extracting(ZoneEventRound::getStatus)
        .containsExactly(RoundStatus.ACTIVE);
  }

  @Test
  @DisplayName("4개 서로 다른 구역 + 활성 타겟이 갖춰지면 schedule로 확정된다")
  void scheduleSucceedsWithFourZonesAndActiveTargets() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    createFourZoneEventsWithTargets(roundId);

    AdminRoundResDto scheduled = consoleService.schedule(operator, roundId);

    assertThat(scheduled.status()).isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  @DisplayName("구역이 4개 미만이면 schedule은 400 계열 예외다")
  void scheduleFailsWithFewerThanFourZones() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    adminZoneEventService.create(operator, zoneEventReq(roundId, "YEONGDO"));

    assertThatThrownBy(() -> consoleService.schedule(operator, roundId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("구역은 4개지만 활성 타겟이 없는 슬롯이 있으면 schedule은 400 계열 예외다")
  void scheduleFailsWithoutActiveTarget() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    for (String zone : List.of("YEONGDO", "OLD_DOWNTOWN", "SUYEONG_NAMGU")) {
      ZoneEvent event =
          zoneEventRepository
              .findById(
                  UUID.fromString(
                      adminZoneEventService
                          .create(operator, zoneEventReq(roundId, zone))
                          .eventId()))
              .orElseThrow();
      authTargetRepository.save(
          ZoneEventAuthTarget.builder()
              .event(event)
              .targetKind(ZoneEventTargetKind.PLACE)
              .placeName("장소")
              .latitude(35.1)
              .longitude(129.1)
              .radiusM(100)
              .build());
    }
    adminZoneEventService.create(operator, zoneEventReq(roundId, "WESTERN_BUSAN")); // 타겟 없음

    assertThatThrownBy(() -> consoleService.schedule(operator, roundId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("PATCH는 expectedRevision이 다르면 409, DRAFT/SCHEDULED에서만 허용된다")
  void patchRequiresMatchingRevisionAndAllowedState() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());

    assertThatThrownBy(
            () ->
                consoleService.patch(
                    operator,
                    roundId,
                    new RoundPatchReqDto(created.revision() + 1, "새 이름", null, null, null, null)))
        .isInstanceOf(ConflictException.class);

    AdminRoundResDto patched =
        consoleService.patch(
            operator,
            roundId,
            new RoundPatchReqDto(created.revision(), "새 이름", null, null, null, null));
    assertThat(patched.name()).isEqualTo("새 이름");

    ZoneEventRound round = roundRepository.findById(roundId).orElseThrow();
    round.confirmSchedule();
    round.activate();
    Long activeRevision = roundRepository.saveAndFlush(round).getRevision();

    assertThatThrownBy(
            () ->
                consoleService.patch(
                    operator,
                    roundId,
                    new RoundPatchReqDto(activeRevision, "다른 이름", null, null, null, null)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("cancel은 이력을 유지한 채 회차와 슬롯을 CANCELLED로 만들고, 이미 종료된 이벤트는 건드리지 않는다")
  void cancelPreservesHistory() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    AdminRoundResDto afterCreate = adminZoneEventServiceCreateAndReturnRound(roundId, "YEONGDO");
    UUID eventId = UUID.fromString(afterCreate.slots().get(0).eventId());
    ZoneEventParticipation joined = joined(zoneEventRepository.findById(eventId).orElseThrow());
    ZoneEvent alreadyClosed = closedZoneEvent(roundId, "OLD_DOWNTOWN");

    AdminRoundResDto cancelled =
        consoleService.cancel(operator, roundId, new RoundCancelReqDto("우천", created.revision()));

    assertThat(cancelled.status()).isEqualTo(RoundStatus.CANCELLED);
    assertThat(cancelled.cancelReason()).isEqualTo("우천");
    assertThat(zoneEventRepository.findById(eventId).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CANCELLED);
    assertThat(participationRepository.findById(joined.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.CANCELLED);
    // 이미 종료(CLOSED)된 이벤트는 markCancelled()가 던지므로 건드리지 않고 그대로 CLOSED여야 한다.
    assertThat(zoneEventRepository.findById(alreadyClosed.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);
  }

  @Test
  @DisplayName("이미 CLOSED인 회차는 cancel할 수 없다")
  void cancelClosedRoundConflicts() {
    ZoneEventRound round = closedRound();
    assertThatThrownBy(
            () ->
                consoleService.cancel(
                    operator, round.getId(), new RoundCancelReqDto("사유", round.getRevision())))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("cancel도 expectedRevision이 다르면 409")
  void cancelWithStaleRevisionConflicts() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    assertThatThrownBy(
            () ->
                consoleService.cancel(
                    operator, roundId, new RoundCancelReqDto("사유", created.revision() + 1)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("open/close 수동 엔드포인트는 더 이상 존재하지 않는다(컴파일 타임 보증)")
  void openCloseMethodsRemoved() {
    for (var method : consoleService.getClass().getMethods()) {
      assertThat(method.getName()).isNotIn("open", "close");
    }
  }

  @Test
  @DisplayName("정산은 미완료 참여를 만료하고 리포트를 저장한다(멱등). TOP_LIKE 지급은 더 이상 settle에서 하지 않는다")
  void settle() {
    ZoneEventRound round = closedRound();
    ZoneEvent event = eventWithExcellence(round.getId());
    ZoneEventParticipation winner = success(event, 10);
    ZoneEventParticipation joinedP = joined(event);

    Map<String, Object> report = consoleService.settle(operator, round.getId());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.SETTLED);
    assertThat(participationRepository.findById(joinedP.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.CANCELLED);
    assertThat(userCouponRepository.findAll()).isEmpty();
    assertThat(settlementReportRepository.findById(round.getId())).isPresent();
    assertThat(winner.getLikeCount()).isEqualTo(10);

    Map<String, Object> again = consoleService.settle(operator, round.getId());
    assertThat(again.get("roundId")).isEqualTo(round.getId().toString());
  }

  @Test
  @DisplayName("정산 리포트가 없으면 404, 있으면 저장된 리포트를 돌려준다")
  void settlementReport() {
    ZoneEventRound round = closedRound();
    assertThatThrownBy(() -> consoleService.settlementReport(operator, round.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
    consoleService.settle(operator, round.getId());
    assertThat(consoleService.settlementReport(operator, round.getId()).get("roundId"))
        .isEqualTo(round.getId().toString());
  }

  @Test
  @DisplayName("예비 타겟을 등록하고 이벤트 인증 타겟을 우천 교체한다")
  void backupAndSwapTarget() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    ZoneEvent event =
        zoneEventRepository
            .findById(
                UUID.fromString(
                    adminZoneEventService
                        .create(operator, zoneEventReq(roundId, "YEONGDO"))
                        .eventId()))
            .orElseThrow();
    ZoneEventAuthTarget original =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeName("원래 장소")
                .latitude(35.1)
                .longitude(129.1)
                .radiusM(100)
                .build());

    consoleService.addBackupTarget(
        operator,
        roundId,
        new BackupTargetReqDto(
            ZoneEventTargetKind.PLACE, null, "실내 대체지", "안내", null, 35.2, 129.2, 80));
    UUID backupId =
        UUID.fromString(consoleService.roundDetail(operator, roundId).backups().get(0).targetId());

    consoleService.swapTarget(operator, roundId, new SwapTargetReqDto(event.getId(), backupId));

    ZoneEventAuthTarget swapped = authTargetRepository.findById(original.getId()).orElseThrow();
    assertThat(swapped.getLatitude()).isEqualTo(35.2);
    assertThat(swapped.getRadiusM()).isEqualTo(80);
  }

  @Test
  @DisplayName("이벤트 미배정 슬롯만 있는 회차도 상세 조회가 가능하다(전환할 이벤트 없음)")
  void unassignedSlotRoundDetailWorks() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    ZoneEventRound round = roundRepository.findById(roundId).orElseThrow();
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId("YEONGDO")
            .build());

    AdminRoundResDto detail = consoleService.roundDetail(operator, roundId);

    assertThat(detail.slots()).hasSize(1);
    assertThat(detail.slots().get(0).eventId()).isNull();
    assertThat(detail.slots().get(0).participantCount()).isZero();
  }

  @Test
  @DisplayName("슬롯 배정을 제안한다")
  void suggestSlotsWorks() {
    assertThat(consoleService.suggestSlots(operator, 6).slots()).hasSize(6);
  }

  @Test
  @DisplayName("목록 조회는 from/to 시간 범위로도 필터링된다")
  void listRoundsFiltersByTimeRange() {
    createDraft();
    OffsetDateTime from = OffsetDateTime.now().minusDays(1);
    OffsetDateTime to = OffsetDateTime.now().plusDays(3);

    AdminRoundPageResDto page = consoleService.listRounds(operator, null, from, to, null, 0, 20);

    assertThat(page.items()).isNotEmpty();
  }

  @Test
  @DisplayName("이벤트 하나를 취소하면 남은 구역이 3개라 schedule이 실패한다")
  void scheduleFailsWhenOneEventCancelled() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    createFourZoneEventsWithTargets(roundId);
    UUID cancelledEventId =
        zoneEventRepository.findByRoundId(roundId).stream()
            .filter(e -> e.getZoneId().equals("YEONGDO"))
            .findFirst()
            .orElseThrow()
            .getId();
    adminZoneEventService.cancel(operator, cancelledEventId);

    assertThatThrownBy(() -> consoleService.schedule(operator, roundId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("취소한 구역에 대체 이벤트를 다시 넣으면 schedule이 성공한다")
  void scheduleSucceedsAfterCancelledZoneIsRefilled() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    createFourZoneEventsWithTargets(roundId);
    UUID cancelledEventId =
        zoneEventRepository.findByRoundId(roundId).stream()
            .filter(e -> e.getZoneId().equals("YEONGDO"))
            .findFirst()
            .orElseThrow()
            .getId();
    adminZoneEventService.cancel(operator, cancelledEventId);

    ZoneEvent replacement =
        zoneEventRepository
            .findById(
                UUID.fromString(
                    adminZoneEventService
                        .create(operator, zoneEventReq(roundId, "YEONGDO"))
                        .eventId()))
            .orElseThrow();
    authTargetRepository.save(
        ZoneEventAuthTarget.builder()
            .event(replacement)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("대체 장소")
            .latitude(35.1)
            .longitude(129.1)
            .radiusM(100)
            .build());

    assertThat(consoleService.schedule(operator, roundId).status())
        .isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  @DisplayName("상세는 슬롯별 실제 카운트를 주고, 목록은 N+1을 피하려고 0으로 준다")
  void listOmitsSlotCountsButDetailIncludesThem() {
    AdminRoundResDto created = createDraft();
    UUID roundId = UUID.fromString(created.roundId());
    ZoneEvent event =
        zoneEventRepository
            .findById(
                UUID.fromString(
                    adminZoneEventService
                        .create(operator, zoneEventReq(roundId, "YEONGDO"))
                        .eventId()))
            .orElseThrow();
    joined(event);

    AdminRoundResDto detail = consoleService.roundDetail(operator, roundId);
    assertThat(detail.slots()).hasSize(1);
    assertThat(detail.slots().get(0).participantCount()).isEqualTo(1);

    AdminRoundResDto listed =
        consoleService.listRounds(operator, null, null, null, "테스트 회차", 0, 50).items().stream()
            .filter(r -> r.roundId().equals(roundId.toString()))
            .findFirst()
            .orElseThrow();
    assertThat(listed.slots()).hasSize(1);
    assertThat(listed.slots().get(0).participantCount()).isZero();
  }

  private int nextRoundNo() {
    return roundNoSeq++;
  }

  private AdminRoundResDto createDraft() {
    return consoleService.createRound(
        operator,
        new RoundCreateReqDto(
            null,
            nextRoundNo(),
            "테스트 회차",
            OffsetDateTime.now(),
            OffsetDateTime.now().plusDays(1),
            null,
            new RewardSnapshotReqDto(null, null, 3, "COUPON_CAFE")));
  }

  private void createFourZoneEventsWithTargets(UUID roundId) {
    for (String zone : List.of("YEONGDO", "OLD_DOWNTOWN", "SUYEONG_NAMGU", "WESTERN_BUSAN")) {
      ZoneEvent event =
          zoneEventRepository
              .findById(
                  UUID.fromString(
                      adminZoneEventService
                          .create(operator, zoneEventReq(roundId, zone))
                          .eventId()))
              .orElseThrow();
      authTargetRepository.save(
          ZoneEventAuthTarget.builder()
              .event(event)
              .targetKind(ZoneEventTargetKind.PLACE)
              .placeName("장소")
              .latitude(35.1)
              .longitude(129.1)
              .radiusM(100)
              .build());
    }
  }

  private AdminRoundResDto adminZoneEventServiceCreateAndReturnRound(UUID roundId, String zone) {
    adminZoneEventService.create(operator, zoneEventReq(roundId, zone));
    return consoleService.roundDetail(operator, roundId);
  }

  private AdminZoneEventCreateReqDto zoneEventReq(UUID roundId, String zoneId) {
    return new AdminZoneEventCreateReqDto(
        zoneId,
        type.getTypeCode(),
        "미션",
        null,
        OffsetDateTime.now().plusDays(1),
        120,
        roundId,
        1,
        new RewardSnapshotReqDto(50, null, null, null),
        null,
        null);
  }

  private ZoneEventRound closedRound() {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(nextRoundNo())
            .startsAt(OffsetDateTime.now().minusHours(2))
            .endsAt(OffsetDateTime.now().minusHours(1))
            .status(RoundStatus.CLOSED)
            .build());
  }

  private ZoneEvent closedZoneEvent(UUID roundId, String zoneId) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId(zoneId)
            .type(type)
            .roundId(roundId)
            .title("이미 종료된 이벤트")
            .startsAt(OffsetDateTime.now().minusHours(2))
            .durationMinutes(60)
            .status(ZoneEventStatus.CLOSED)
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
