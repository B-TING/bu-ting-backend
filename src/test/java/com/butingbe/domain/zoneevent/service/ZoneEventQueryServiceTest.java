package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventSummaryResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZoneEventQueryServiceTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID PARTICIPATION_ID =
      UUID.fromString("33333333-0000-0000-0000-000000000001");

  @Mock private ZoneEventRepository zoneEventRepository;
  @Mock private ZoneEventAuthTargetRepository authTargetRepository;
  @Mock private ZoneEventParticipationRepository participationRepository;
  @Mock private FileStorageService fileStorageService;

  private ZoneEventQueryService service;
  private ZoneEvent event;
  private ZoneEventAuthTarget target;

  @BeforeEach
  void setUp() {
    service =
        new ZoneEventQueryService(
            zoneEventRepository, authTargetRepository, participationRepository, fileStorageService);
    event = event();
    target = target(event);
  }

  @Test
  @DisplayName("활성 이벤트 목록을 구역으로 조회하고 남은 시간·성공 수를 채운다")
  void listsActiveEvents() {
    when(zoneEventRepository.findByZoneIdAndStatusOrderByStartsAtAsc(
            "SUYEONG_NAMGU", ZoneEventStatus.ACTIVE))
        .thenReturn(List.of(event));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target));
    when(participationRepository.countByEvent_IdAndStatus(EVENT_ID, ParticipationStatus.SUCCESS))
        .thenReturn(27L);

    List<ZoneEventSummaryResDto> result = service.getActiveEvents("SUYEONG_NAMGU", null);

    assertThat(result).hasSize(1);
    ZoneEventSummaryResDto summary = result.get(0);
    assertThat(summary.zone().zoneId()).isEqualTo("SUYEONG_NAMGU");
    assertThat(summary.successCount()).isEqualTo(27);
    assertThat(summary.remainingSeconds()).isPositive();
    assertThat(summary.baseReward().points()).isEqualTo(50);
    assertThat(summary.authTarget().radiusM()).isEqualTo(100);
    // 비로그인 개인화 필드는 null
    assertThat(summary.myParticipationStatus()).isNull();
    assertThat(summary.myOpenParticipationId()).isNull();
  }

  @Test
  @DisplayName("로그인 시 열린 참여가 있으면 내 참여 상태를 채운다")
  void fillsMyParticipationWhenLoggedIn() {
    ZoneEventParticipation open =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(USER_ID)
            .status(ParticipationStatus.JOINED)
            .gpsLat(35.15)
            .gpsLng(129.11)
            .joinedAt(OffsetDateTime.now())
            .build();
    ReflectionTestUtils.setField(open, "id", PARTICIPATION_ID);
    when(zoneEventRepository.findByZoneIdAndStatusOrderByStartsAtAsc(any(), any()))
        .thenReturn(List.of(event));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target));
    when(participationRepository.countByEvent_IdAndStatus(any(), any())).thenReturn(0L);
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.of(open));

    ZoneEventSummaryResDto summary = service.getActiveEvents("SUYEONG_NAMGU", USER_ID).get(0);

    assertThat(summary.myParticipationStatus()).isEqualTo("JOINED");
    assertThat(summary.myOpenParticipationId()).isEqualTo(PARTICIPATION_ID);
  }

  @Test
  @DisplayName("잘못된 구역은 400(invalid_zone) 예외다")
  void rejectsInvalidZone() {
    assertThatThrownBy(() -> service.getActiveEvents("NOWHERE", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.invalid_zone");
  }

  @Test
  @DisplayName("상세는 예시 이미지 presigned URL·우수 보상·남은 참여 횟수를 채운다")
  void detailFillsExampleUrlAndRemainingAttempts() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target));
    when(participationRepository.countByEvent_IdAndStatus(EVENT_ID, ParticipationStatus.SUCCESS))
        .thenReturn(3L);
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(
            EVENT_ID, USER_ID, ParticipationStatus.SUCCESS))
        .thenReturn(0L);
    when(fileStorageService.getPresignedUrl("uploads/example.jpg"))
        .thenReturn("https://signed.example/uploads/example.jpg");

    ZoneEventDetailResDto detail = service.getEventDetail(EVENT_ID, USER_ID);

    assertThat(detail.successCount()).isEqualTo(3);
    assertThat(detail.myRemainingAttempts()).isEqualTo(1);
    assertThat(detail.excellenceReward().topN()).isEqualTo(5);
    assertThat(detail.authTarget().exampleImageUrl())
        .isEqualTo("https://signed.example/uploads/example.jpg");
    assertThat(detail.authTarget().guideText()).isEqualTo("가로로 촬영");
  }

  @Test
  @DisplayName("비로그인 상세는 남은 참여 횟수가 null이고, 예시 이미지가 없으면 URL도 null이다")
  void detailWithoutLoginAndWithoutExample() {
    ZoneEventAuthTarget noImage = target(event);
    ReflectionTestUtils.setField(noImage, "exampleFileKey", null);
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(noImage));
    when(participationRepository.countByEvent_IdAndStatus(any(), any())).thenReturn(0L);

    ZoneEventDetailResDto detail = service.getEventDetail(EVENT_ID, null);

    assertThat(detail.myRemainingAttempts()).isNull();
    assertThat(detail.authTarget().exampleImageUrl()).isNull();
  }

  @Test
  @DisplayName("없는 이벤트 상세는 404다")
  void detailNotFound() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getEventDetail(EVENT_ID, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.not_found");
  }

  private ZoneEvent event() {
    ZoneEventType type =
        ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build();
    ZoneEvent created =
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("광안대교 야경 담기")
            .description("야경 촬영")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(1440)
            .status(ZoneEventStatus.ACTIVE)
            .baseReward(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null))
            .excellenceReward(new RewardSnapshot(null, null, 5, "COUPON_CAFE_3000"))
            .successLimitPerUser(1)
            .build();
    ReflectionTestUtils.setField(created, "id", EVENT_ID);
    return created;
  }

  private ZoneEventAuthTarget target(ZoneEvent event) {
    ZoneEventAuthTarget created =
        ZoneEventAuthTarget.builder()
            .event(event)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("광안대교 야경")
            .guideText("가로로 촬영")
            .exampleFileKey("uploads/example.jpg")
            .latitude(35.153)
            .longitude(129.118)
            .radiusM(100)
            .build();
    ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
    return created;
  }
}
