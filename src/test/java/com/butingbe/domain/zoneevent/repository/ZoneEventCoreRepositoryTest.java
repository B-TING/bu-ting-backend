package com.butingbe.domain.zoneevent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventCoreRepositoryTest extends AbstractContainerTest {

  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventAuthTargetRepository zoneEventAuthTargetRepository;
  @Autowired private ZoneEventParticipationRepository zoneEventParticipationRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("보상 스냅샷(jsonb)이 왕복 저장·조회되고 종료 시각을 계산한다")
  void persistsEventWithJsonbRewardSnapshot() {
    ZoneEvent event = zoneEventRepository.save(activeEvent("SUYEONG_NAMGU"));

    ZoneEvent found = zoneEventRepository.findById(event.getId()).orElseThrow();
    assertThat(found.getBaseReward())
        .isEqualTo(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null));
    assertThat(found.getExcellenceReward().topN()).isEqualTo(5);
    assertThat(found.getZoneId()).isEqualTo("SUYEONG_NAMGU");
    assertThat(found.getType().getTypeCode()).isEqualTo("PLACE_AUTH");
    assertThat(found.getSuccessLimitPerUser()).isEqualTo(1);
    assertThat(found.endsAt()).isEqualTo(found.getStartsAt().plusMinutes(1440));
  }

  @Test
  @DisplayName("구역과 상태로 활성 이벤트를 조회한다")
  void findsActiveEventsByZoneAndStatus() {
    zoneEventRepository.save(activeEvent("YEONGDO"));
    zoneEventRepository.save(activeEvent("SUYEONG_NAMGU"));

    List<ZoneEvent> active =
        zoneEventRepository.findByZoneIdAndStatusOrderByStartsAtAsc(
            "YEONGDO", ZoneEventStatus.ACTIVE);

    assertThat(active).hasSize(1);
    assertThat(active.get(0).getZoneId()).isEqualTo("YEONGDO");
  }

  @Test
  @DisplayName("인증 타겟을 이벤트로 조회한다")
  void findsAuthTargetByEvent() {
    ZoneEvent event = zoneEventRepository.save(activeEvent("CENTRAL_NORTH"));
    zoneEventAuthTargetRepository.save(
        ZoneEventAuthTarget.builder()
            .event(event)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("광안대교")
            .guideText("가로로 촬영")
            .latitude(35.153)
            .longitude(129.118)
            .radiusM(100)
            .build());

    ZoneEventAuthTarget target =
        zoneEventAuthTargetRepository.findByEvent_Id(event.getId()).orElseThrow();
    assertThat(target.getRadiusM()).isEqualTo(100);
    assertThat(target.getTargetKind()).isEqualTo(ZoneEventTargetKind.PLACE);
  }

  @Test
  @DisplayName("열린 참여를 유저·이벤트·상태로 조회하고 성공 수를 센다")
  void findsOpenParticipationAndCountsSuccess() {
    ZoneEvent event = zoneEventRepository.save(activeEvent("OLD_DOWNTOWN"));
    User user = userRepository.save(user());
    ZoneEventParticipation joined =
        zoneEventParticipationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(user.getId())
                .status(ParticipationStatus.JOINED)
                .gpsLat(35.15)
                .gpsLng(129.11)
                .joinedAt(OffsetDateTime.now())
                .build());

    assertThat(
            zoneEventParticipationRepository.findByEvent_IdAndUserIdAndStatusIn(
                event.getId(),
                user.getId(),
                ParticipationStatus.JOINED.isOpen()
                    ? List.of(
                        ParticipationStatus.JOINED,
                        ParticipationStatus.SUBMITTED,
                        ParticipationStatus.UNDER_REVIEW)
                    : List.of()))
        .isPresent();
    assertThat(joined.getVisibility()).isEqualTo(ParticipationVisibility.PUBLIC);
    assertThat(joined.getHidden()).isFalse();
    assertThat(joined.getLikeCount()).isZero();
    assertThat(
            zoneEventParticipationRepository.countByEvent_IdAndUserIdAndStatus(
                event.getId(), user.getId(), ParticipationStatus.SUCCESS))
        .isZero();
  }

  private ZoneEvent activeEvent(String zoneId) {
    ZoneEventType type =
        zoneEventTypeRepository
            .findById("PLACE_AUTH")
            .orElseGet(
                () ->
                    zoneEventTypeRepository.save(
                        ZoneEventType.builder()
                            .typeCode("PLACE_AUTH")
                            .name("장소 인증")
                            .requiresUpload(true)
                            .description("장소 인증")
                            .build()));
    return ZoneEvent.builder()
        .zoneId(zoneId)
        .type(type)
        .title("광안대교 야경 담기")
        .description("야경 촬영")
        .startsAt(OffsetDateTime.now())
        .durationMinutes(1440)
        .status(ZoneEventStatus.ACTIVE)
        .baseReward(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null))
        .excellenceReward(new RewardSnapshot(null, null, 5, "COUPON_CAFE_3000"))
        .successLimitPerUser(1)
        .build();
  }

  private User user() {
    return User.builder()
        .email("zone-" + UUID.randomUUID() + "@example.com")
        .provider("google")
        .providerId("google-zone-" + UUID.randomUUID())
        .name(new Name("Kim", "Tester"))
        .nickname("zoner")
        .role(UserRole.USER)
        .build();
  }
}
