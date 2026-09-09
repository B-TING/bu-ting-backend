package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminTopNResDto;
import com.butingbe.domain.zoneevent.dto.response.TopNZoneGroupResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
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
  @Autowired private ZoneEventReportRepository reportRepository;
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
