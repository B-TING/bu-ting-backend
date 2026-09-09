package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.WinnerConfirmReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminTopNResDto;
import com.butingbe.domain.zoneevent.dto.response.TopNZoneGroupResDto;
import com.butingbe.domain.zoneevent.dto.response.WinnerConfirmResDto;
import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.IdempotencyRecordRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
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
class AdminZoneEventWinnerServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventWinnerService winnerService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventRankingSnapshotService snapshotService;
  @Autowired private ZoneEventRankingSnapshotRepository snapshotRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private IdempotencyService idempotencyService;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private UserRepository userRepository;

  private ZoneEventRound round;
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
    round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundType(RoundType.REGULAR)
                .roundNo(1)
                .name("1회차")
                .startsAt(OffsetDateTime.now().minusDays(2))
                .endsAt(OffsetDateTime.now().minusHours(1))
                .build());
    event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .roundId(round.getId())
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusDays(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .excellenceReward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .successLimitPerUser(10)
                .build());
    operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("경계 동점 후보 전원과 미해결 신고 보류 플래그를 함께 돌려준다")
  void topNReturnsTieCandidatesWithHeldFlag() {
    ZoneEventParticipation tie1 = success(7);
    ZoneEventParticipation tie2 = success(7);
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(tie2.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());

    AdminTopNResDto result = winnerService.topN(operator, round.getId(), null);

    assertThat(result.zones()).hasSize(1);
    TopNZoneGroupResDto zone = result.zones().get(0);
    assertThat(zone.eventId()).isEqualTo(event.getId().toString());
    assertThat(zone.candidates()).hasSize(2);
    assertThat(zone.candidates()).allMatch(c -> c.tied());
    assertThat(
            zone.candidates().stream()
                .filter(c -> c.participationId().equals(tie2.getId().toString())))
        .allMatch(c -> c.heldByReport());
    assertThat(
            zone.candidates().stream()
                .filter(c -> c.participationId().equals(tie1.getId().toString())))
        .allMatch(c -> !c.heldByReport());
  }

  @Test
  @DisplayName("아직 회차가 종료되지 않아 스냅샷이 없으면 빈 후보 목록을 돌려준다")
  void topNEmptyWhenNotFrozenYet() {
    success(7);

    AdminTopNResDto result = winnerService.topN(operator, round.getId(), null);

    assertThat(result.zones().get(0).candidates()).isEmpty();
  }

  @Test
  @DisplayName("운영자가 아니면 403")
  void topNForbidden() {
    AuthenticatedUser normal =
        new AuthenticatedUser(savedUser("normal").getId(), "n@example.com", "n", List.of());
    assertThatThrownBy(() -> winnerService.topN(normal, round.getId(), null))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("존재하지 않는 roundId는 404")
  void topNRoundNotFound() {
    assertThatThrownBy(() -> winnerService.topN(operator, UUID.randomUUID(), null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("eventId를 지정하면 해당 이벤트의 구역만 돌려준다")
  void topNFiltersByEventId() {
    ZoneEventType otherType =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH_2")
                .name("장소 인증2")
                .requiresUpload(true)
                .build());
    ZoneEvent otherEvent =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("HAEUNDAE_GIJANG")
                .type(otherType)
                .roundId(round.getId())
                .title("다른 이벤트")
                .startsAt(OffsetDateTime.now().minusDays(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .excellenceReward(new RewardSnapshot(null, null, 1, "COUPON_TOP"))
                .successLimitPerUser(10)
                .build());

    success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());

    ZoneEventParticipation otherParticipation =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(otherEvent)
                .userId(UUID.randomUUID())
                .status(ParticipationStatus.SUCCESS)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    ReflectionTestUtils.setField(otherParticipation, "likeCount", 3L);
    participationRepository.save(otherParticipation);
    otherEvent.close();
    snapshotService.freeze(otherEvent, OffsetDateTime.now());

    AdminTopNResDto result = winnerService.topN(operator, round.getId(), event.getId());

    assertThat(result.zones()).hasSize(1);
    TopNZoneGroupResDto zone = result.zones().get(0);
    assertThat(zone.eventId()).isEqualTo(event.getId().toString());
    assertThat(zone.zoneId()).isEqualTo("SUYEONG_NAMGU");
  }

  @Test
  @DisplayName("동점 후보 중 선택한 참여만 finalized로 확정하고 감사 로그를 남긴다")
  void confirmWinnersFinalizesSelectedOnly() {
    ZoneEventParticipation tie1 = success(7);
    ZoneEventParticipation tie2 = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchorSnapshotId =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, tie1.getId())
            .orElseThrow()
            .getId();

    WinnerConfirmResDto result =
        winnerService.confirmWinners(
            operator,
            event.getId(),
            new WinnerConfirmReqDto(anchorSnapshotId, List.of(tie1.getId()), "먼저 제출한 참여자 우선 선정", 1),
            null);

    assertThat(result.confirmedParticipationIds()).containsExactly(tie1.getId().toString());
    assertThat(
            snapshotRepository
                .findByEventIdAndVersionAndParticipationId(event.getId(), 1, tie1.getId())
                .orElseThrow()
                .getFinalized())
        .isTrue();
    assertThat(
            snapshotRepository
                .findByEventIdAndVersionAndParticipationId(event.getId(), 1, tie2.getId())
                .orElseThrow()
                .getFinalized())
        .isFalse();
    List<ZoneEventAuditLog> logs =
        auditLogRepository.findByTargetTypeAndTargetId("EVENT", event.getId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getAction()).isEqualTo("CONFIRM_WINNERS");
    assertThat(logs.get(0).getDetail())
        .containsEntry("selectionReason", "먼저 제출한 참여자 우선 선정")
        .containsEntry("version", 1)
        .containsEntry("participationIds", List.of(tie1.getId().toString()));
  }

  @Test
  @DisplayName("expectedRevision이 스냅샷 version과 다르면 409")
  void confirmWinnersStaleRevisionConflicts() {
    ZoneEventParticipation p = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID snapshotId =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, p.getId())
            .orElseThrow()
            .getId();

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(snapshotId, List.of(p.getId()), "사유", 99),
                    null))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("미해결 신고가 있는 참여는 확정할 수 없다(409)")
  void confirmWinnersHeldByReportConflicts() {
    ZoneEventParticipation p = success(7);
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID snapshotId =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, p.getId())
            .orElseThrow()
            .getId();

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(snapshotId, List.of(p.getId()), "사유", 1),
                    null))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("보류 해제 후 다시 confirm하면 기존 확정자는 그대로 두고 새로 추가 확정한다")
  void confirmWinnersIsAdditiveAfterHoldResolved() {
    setTopN(2);
    ZoneEventParticipation tiedClear = success(7);
    ZoneEventParticipation tiedHeld = success(7);
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(tiedHeld.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchor =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, tiedClear.getId())
            .orElseThrow()
            .getId();
    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(tiedClear.getId()), "사유", 1),
        null);

    report.resolveAs(com.butingbe.domain.zoneevent.entity.ReportStatus.DISMISSED);
    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(tiedHeld.getId()), "보류 해제 후 확정", 1),
        null);

    assertThat(
            snapshotRepository
                .findByEventIdAndVersionAndParticipationId(event.getId(), 1, tiedClear.getId())
                .orElseThrow()
                .getFinalized())
        .isTrue();
    assertThat(
            snapshotRepository
                .findByEventIdAndVersionAndParticipationId(event.getId(), 1, tiedHeld.getId())
                .orElseThrow()
                .getFinalized())
        .isTrue();
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("EVENT", event.getId())).hasSize(2);
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 재전송하면 다시 처리하지 않고 이전 결과를 그대로 돌려준다")
  void confirmWinnersIsIdempotent() {
    ZoneEventParticipation p = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID snapshotId =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, p.getId())
            .orElseThrow()
            .getId();
    String key = "idem-" + UUID.randomUUID();
    WinnerConfirmReqDto request = new WinnerConfirmReqDto(snapshotId, List.of(p.getId()), "사유", 1);

    WinnerConfirmResDto first = winnerService.confirmWinners(operator, event.getId(), request, key);
    WinnerConfirmResDto replay =
        winnerService.confirmWinners(operator, event.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("멱등성 키 재사용 시 저장된 응답을 그대로 재생하며, 손상된 JSON이면 500 대신 예외를 던진다")
  void confirmWinnersReplayWithCorruptedJsonThrows() {
    ZoneEventParticipation p = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID snapshotId =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, p.getId())
            .orElseThrow()
            .getId();
    String key = "idem-corrupt-" + UUID.randomUUID();
    List<UUID> sortedIds = List.of(p.getId()).stream().sorted().toList();
    String fingerprint = event.getId() + ":" + snapshotId + ":" + sortedIds + ":" + "사유" + ":" + 1;
    idempotencyRecordRepository.save(
        new IdempotencyRecord(key, "zone-event-winner-confirm", fingerprint, "not-a-json"));

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(snapshotId, List.of(p.getId()), "사유", 1),
                    key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to deserialize");
  }

  @Test
  @DisplayName("존재하지 않는 snapshotId를 anchor로 지정하면 404")
  void confirmWinnersAnchorSnapshotNotFound() {
    ZoneEventParticipation p = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(UUID.randomUUID(), List.of(p.getId()), "사유", 1),
                    null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("요청한 participationId에 해당하는 스냅샷 행이 없으면 404")
  void confirmWinnersParticipationSnapshotNotFound() {
    ZoneEventParticipation p = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchorSnapshotId =
        snapshotRepository
            .findByEventIdAndVersionAndParticipationId(event.getId(), 1, p.getId())
            .orElseThrow()
            .getId();

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(anchorSnapshotId, List.of(UUID.randomUUID()), "사유", 1),
                    null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("정원(topN)만큼 한 번에 확정하는 것은 성공한다")
  void confirmWinnersUpToTopNSucceeds() {
    setTopN(2);
    ZoneEventParticipation tie1 = success(7);
    ZoneEventParticipation tie2 = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());

    WinnerConfirmResDto result =
        winnerService.confirmWinners(
            operator,
            event.getId(),
            new WinnerConfirmReqDto(
                snapshotIdOf(tie1), List.of(tie1.getId(), tie2.getId()), "정원만큼 확정", 1),
            null);

    assertThat(result.confirmedParticipationIds()).hasSize(2);
    assertThat(snapshotRepository.countByEventIdAndVersionAndFinalizedTrue(event.getId(), 1))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("한 번의 요청으로 정원(topN)을 넘겨 확정하면 409")
  void confirmWinnersBeyondTopNInOneRequestConflicts() {
    setTopN(2);
    ZoneEventParticipation tie1 = success(7);
    ZoneEventParticipation tie2 = success(7);
    ZoneEventParticipation tie3 = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchor = snapshotIdOf(tie1);

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(
                        anchor, List.of(tie1.getId(), tie2.getId(), tie3.getId()), "초과 확정", 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.winner.topn_exceeded");
    assertThat(snapshotRepository.countByEventIdAndVersionAndFinalizedTrue(event.getId(), 1))
        .isZero();
  }

  @Test
  @DisplayName("두 번에 나눠 정원(topN)까지 확정하는 것은 성공한다")
  void confirmWinnersAdditiveUpToTopNSucceeds() {
    setTopN(2);
    ZoneEventParticipation tie1 = success(7);
    ZoneEventParticipation tie2 = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchor = snapshotIdOf(tie1);

    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(tie1.getId()), "1차", 1),
        null);
    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(tie2.getId()), "2차", 1),
        null);

    assertThat(snapshotRepository.countByEventIdAndVersionAndFinalizedTrue(event.getId(), 1))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("이미 정원(topN)을 채운 뒤 한 명을 더 추가 확정하면 409")
  void confirmWinnersBeyondTopNAcrossCallsConflicts() {
    setTopN(2);
    ZoneEventParticipation tie1 = success(7);
    ZoneEventParticipation tie2 = success(7);
    ZoneEventParticipation tie3 = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchor = snapshotIdOf(tie1);
    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(tie1.getId(), tie2.getId()), "정원 확정", 1),
        null);

    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(anchor, List.of(tie3.getId()), "초과 추가", 1),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.winner.topn_exceeded");
    assertThat(snapshotRepository.countByEventIdAndVersionAndFinalizedTrue(event.getId(), 1))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("이미 확정된 참여를 다시 보내도 정원을 새로 소모하지 않는다(멱등 no-op)")
  void reconfirmingFinalizedIdDoesNotConsumeTopN() {
    ZoneEventParticipation only = success(7);
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());
    UUID anchor = snapshotIdOf(only);
    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(only.getId()), "확정", 1),
        null);

    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(only.getId()), "재확정", 1),
        null);

    assertThat(snapshotRepository.countByEventIdAndVersionAndFinalizedTrue(event.getId(), 1))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("신고 누적으로 숨겨진 참여도 스냅샷에 남아, 신고가 해제되면 수상자로 확정할 수 있다")
  void autoHiddenParticipationSurvivesFreezeAndIsConfirmableAfterReportResolved() {
    ZoneEventParticipation hidden = success(7);
    hidden.hide();
    participationRepository.save(hidden);
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(hidden.getId())
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    event.close();
    snapshotService.freeze(event, OffsetDateTime.now());

    // 1) 숨김 상태여도 스냅샷 행은 존재한다.
    UUID anchor = snapshotIdOf(hidden);
    AdminTopNResDto topN = winnerService.topN(operator, round.getId(), event.getId());
    assertThat(topN.zones().get(0).candidates()).hasSize(1);
    // 2) 미해결 신고가 남아 있는 동안은 기존 heldByReport 판정으로 확정이 막힌다.
    assertThat(topN.zones().get(0).candidates().get(0).heldByReport()).isTrue();
    assertThatThrownBy(
            () ->
                winnerService.confirmWinners(
                    operator,
                    event.getId(),
                    new WinnerConfirmReqDto(anchor, List.of(hidden.getId()), "보류 중", 1),
                    null))
        .isInstanceOf(ConflictException.class);

    // 3) 신고가 기각되면 같은 스냅샷 행으로 확정할 수 있다.
    report.resolveAs(com.butingbe.domain.zoneevent.entity.ReportStatus.DISMISSED);
    hidden.unhide();
    participationRepository.save(hidden);
    winnerService.confirmWinners(
        operator,
        event.getId(),
        new WinnerConfirmReqDto(anchor, List.of(hidden.getId()), "신고 기각 후 확정", 1),
        null);

    assertThat(
            snapshotRepository
                .findByEventIdAndVersionAndParticipationId(event.getId(), 1, hidden.getId())
                .orElseThrow()
                .getFinalized())
        .isTrue();
  }

  /** 이 테스트 픽스처의 이벤트 우수 보상 정원(topN)을 바꾼다. */
  private void setTopN(int topN) {
    ReflectionTestUtils.setField(
        event, "excellenceReward", new RewardSnapshot(null, null, topN, "COUPON_TOP"));
  }

  private UUID snapshotIdOf(ZoneEventParticipation participation) {
    return snapshotRepository
        .findByEventIdAndVersionAndParticipationId(event.getId(), 1, participation.getId())
        .orElseThrow()
        .getId();
  }

  private ZoneEventParticipation success(long likeCount) {
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(UUID.randomUUID())
                .status(ParticipationStatus.SUCCESS)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    ReflectionTestUtils.setField(p, "likeCount", likeCount);
    return participationRepository.save(p);
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
