package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventRankingSnapshotServiceTest extends AbstractContainerTest {

  @Autowired private ZoneEventRankingSnapshotService snapshotService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventRankingSnapshotRepository snapshotRepository;

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
  }

  @Test
  @DisplayName("동점 없이 딱 topN이면 전부 tied=false로 기록한다")
  void noTiesAtBoundary() {
    ZoneEvent event = eventWithTopN(2);
    ZoneEventParticipation first = success(event, 10);
    ZoneEventParticipation second = success(event, 8);
    success(event, 5); // 컷오프(8) 미만 → 제외

    snapshotService.freeze(event, OffsetDateTime.now());

    List<ZoneEventRankingSnapshot> rows =
        snapshotRepository.findByEventIdAndVersionOrderByRankNAsc(event.getId(), 1);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getParticipationId()).isEqualTo(first.getId());
    assertThat(rows.get(0).getRankN()).isEqualTo(1);
    assertThat(rows.get(0).getTied()).isFalse();
    assertThat(rows.get(1).getParticipationId()).isEqualTo(second.getId());
    assertThat(rows.get(1).getTied()).isFalse();
  }

  @Test
  @DisplayName("경계에서 동점이면 동점자 전원을 tied=true로 포함한다")
  void tiesAtBoundaryAllIncluded() {
    ZoneEvent event = eventWithTopN(2);
    ZoneEventParticipation clear = success(event, 10);
    ZoneEventParticipation tie1 = success(event, 7);
    ZoneEventParticipation tie2 = success(event, 7);
    ZoneEventParticipation tie3 = success(event, 7);
    success(event, 3); // 컷오프(7) 미만 → 제외

    snapshotService.freeze(event, OffsetDateTime.now());

    List<ZoneEventRankingSnapshot> rows =
        snapshotRepository.findByEventIdAndVersionOrderByRankNAsc(event.getId(), 1);
    assertThat(rows).hasSize(4); // clear + 3 tied
    assertThat(rows.get(0).getParticipationId()).isEqualTo(clear.getId());
    assertThat(rows.get(0).getRankN()).isEqualTo(1);
    assertThat(rows.get(0).getTied()).isFalse();
    assertThat(rows.subList(1, 4))
        .extracting(ZoneEventRankingSnapshot::getParticipationId)
        .containsExactlyInAnyOrder(tie1.getId(), tie2.getId(), tie3.getId());
    assertThat(rows.subList(1, 4)).allMatch(r -> r.getRankN() == 2 && r.getTied());
  }

  @Test
  @DisplayName("우수 보상이 없는 이벤트는 스냅샷을 만들지 않는다")
  void skipsWithoutExcellenceReward() {
    ZoneEvent event = zoneEventRepository.save(baseEventBuilder().excellenceReward(null).build());
    success(event, 10);

    snapshotService.freeze(event, OffsetDateTime.now());

    assertThat(snapshotRepository.countByEventId(event.getId())).isZero();
  }

  @Test
  @DisplayName("이미 스냅샷이 있으면 다시 얼리지 않는다(방어적 멱등)")
  void skipsIfAlreadyFrozen() {
    ZoneEvent event = eventWithTopN(1);
    success(event, 5);
    snapshotService.freeze(event, OffsetDateTime.now());
    int countAfterFirst = snapshotRepository.countByEventId(event.getId());

    success(event, 99); // 다시 얼려진다면 이 참여도 잡혀야 하지만, 방어적 멱등이라 무시된다
    snapshotService.freeze(event, OffsetDateTime.now());

    assertThat(snapshotRepository.countByEventId(event.getId())).isEqualTo(countAfterFirst);
  }

  private ZoneEvent eventWithTopN(int topN) {
    return zoneEventRepository.save(
        baseEventBuilder()
            .excellenceReward(new RewardSnapshot(null, null, topN, "COUPON_TOP"))
            .build());
  }

  private ZoneEvent.ZoneEventBuilder baseEventBuilder() {
    return ZoneEvent.builder()
        .zoneId("SUYEONG_NAMGU")
        .type(type)
        .title("이벤트")
        .startsAt(OffsetDateTime.now().minusDays(1))
        .durationMinutes(1440)
        .status(ZoneEventStatus.ACTIVE)
        .baseReward(new RewardSnapshot(50, null, null, null))
        .successLimitPerUser(10);
  }

  private ZoneEventParticipation success(ZoneEvent event, long likeCount) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(UUID.randomUUID())
            .status(com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    p = participationRepository.save(p);
    ReflectionTestUtils.setField(p, "likeCount", likeCount);
    ReflectionTestUtils.setField(p, "completedAt", OffsetDateTime.now());
    return participationRepository.save(p);
  }
}
