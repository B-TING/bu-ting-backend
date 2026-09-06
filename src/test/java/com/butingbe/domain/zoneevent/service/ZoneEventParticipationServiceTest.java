package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.exception.OpenParticipationExistsException;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZoneEventParticipationServiceTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID OPEN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID OTHER_ID = UUID.fromString("22222222-0000-0000-0000-000000000009");
  private static final double IN_LAT = 35.1532;
  private static final double IN_LNG = 129.1182;
  private static final double OUT_LAT = 35.16;
  private static final double OUT_LNG = 129.13;

  @Mock private ZoneEventRepository zoneEventRepository;
  @Mock private ZoneEventAuthTargetRepository authTargetRepository;
  @Mock private ZoneEventParticipationRepository participationRepository;

  private ZoneEventParticipationService service;
  private AuthenticatedUser user;
  private ZoneEvent event;

  @BeforeEach
  void setUp() {
    service =
        new ZoneEventParticipationService(
            zoneEventRepository, authTargetRepository, participationRepository);
    user = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    event = event(ZoneEventStatus.ACTIVE, 1);
  }

  @Test
  @DisplayName("반경 이내면 JOINED 참여를 만들고 거리를 채운다")
  void joinWithinRadius() {
    stubActiveEventWithTarget();
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.empty());
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(
            EVENT_ID, USER_ID, ParticipationStatus.SUCCESS))
        .thenReturn(0L);
    when(participationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              ZoneEventParticipation p = invocation.getArgument(0);
              ReflectionTestUtils.setField(p, "id", OPEN_ID);
              return p;
            });

    ParticipationResDto result = service.join(user, EVENT_ID, IN_LAT, IN_LNG);

    assertThat(result.status()).isEqualTo("JOINED");
    assertThat(result.distanceM()).isEqualTo(28);
    assertThat(result.zoneId()).isEqualTo("SUYEONG_NAMGU");
    assertThat(result.visibility()).isEqualTo("PUBLIC");
  }

  @Test
  @DisplayName("반경 밖이면 400(out_of_range)이고 거리를 담는다")
  void joinOutOfRange() {
    stubActiveEventWithTarget();

    assertThatThrownBy(() -> service.join(user, EVENT_ID, OUT_LAT, OUT_LNG))
        .isInstanceOf(ZoneEventOutOfRangeException.class)
        .satisfies(
            e ->
                assertThat(((ZoneEventOutOfRangeException) e).getDistanceMeters()).isEqualTo(1340));
  }

  @Test
  @DisplayName("이미 열린 참여가 있으면 409(already_open)에 기존 id를 담는다")
  void joinWhenOpenExists() {
    stubActiveEventWithTarget();
    ZoneEventParticipation open = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(open, "id", OPEN_ID);
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.of(open));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(OpenParticipationExistsException.class)
        .satisfies(
            e ->
                assertThat(((OpenParticipationExistsException) e).getParticipationId())
                    .isEqualTo(OPEN_ID));
  }

  @Test
  @DisplayName("성공 상한에 도달하면 409(limit_reached)다")
  void joinWhenLimitReached() {
    stubActiveEventWithTarget();
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.empty());
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(
            EVENT_ID, USER_ID, ParticipationStatus.SUCCESS))
        .thenReturn(1L);

    assertThatThrownBy(() -> service.join(user, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.limit_reached");
  }

  @Test
  @DisplayName("동시 요청으로 부분 UK를 위반하면 409(already_open)로 매핑한다")
  void joinConcurrentConflict() {
    stubActiveEventWithTarget();
    ZoneEventParticipation open = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(open, "id", OPEN_ID);
    when(participationRepository.findByEvent_IdAndUserIdAndStatusIn(
            eq(EVENT_ID), eq(USER_ID), any()))
        .thenReturn(Optional.empty(), Optional.of(open));
    when(participationRepository.countByEvent_IdAndUserIdAndStatus(any(), any(), any()))
        .thenReturn(0L);
    when(participationRepository.save(any()))
        .thenThrow(new DataIntegrityViolationException("uk_zone_event_participation_open"));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(OpenParticipationExistsException.class)
        .satisfies(
            e ->
                assertThat(((OpenParticipationExistsException) e).getParticipationId())
                    .isEqualTo(OPEN_ID));
  }

  @Test
  @DisplayName("ACTIVE가 아니면 409(not_active)다")
  void joinWhenNotActive() {
    when(zoneEventRepository.findById(EVENT_ID))
        .thenReturn(Optional.of(event(ZoneEventStatus.SCHEDULED, 1)));

    assertThatThrownBy(() -> service.join(user, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.not_active");
  }

  @Test
  @DisplayName("없는 이벤트는 404다")
  void joinWhenEventNotFound() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.join(user, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.not_found");
  }

  @Test
  @DisplayName("타겟이 없는 이벤트는 404다")
  void joinWhenTargetMissing() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.join(user, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("미인증이면 401이다")
  void joinWhenUnauthenticated() {
    assertThatThrownBy(() -> service.join(null, EVENT_ID, IN_LAT, IN_LNG))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private void stubActiveEventWithTarget() {
    when(zoneEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    lenient()
        .when(authTargetRepository.findByEvent_Id(EVENT_ID))
        .thenReturn(Optional.of(target(event)));
  }

  @Test
  @DisplayName("열린 참여를 취소하면 CANCELLED가 된다")
  void cancelOpenParticipation() {
    ZoneEventParticipation joined = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(joined, "id", OPEN_ID);
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.of(joined));

    service.cancel(user, EVENT_ID, OPEN_ID);

    assertThat(joined.getStatus()).isEqualTo(ParticipationStatus.CANCELLED);
    assertThat(joined.getCancelReason()).isEqualTo("USER");
  }

  @Test
  @DisplayName("SUCCESS 참여는 취소할 수 없다(409)")
  void cancelSuccessRejected() {
    ZoneEventParticipation success = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(success, "id", OPEN_ID);
    ReflectionTestUtils.setField(success, "status", ParticipationStatus.SUCCESS);
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.of(success));

    assertThatThrownBy(() -> service.cancel(user, EVENT_ID, OPEN_ID))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("타인의 참여는 취소할 수 없다(403)")
  void cancelOthersForbidden() {
    ZoneEventParticipation joined = ZoneEventParticipation.join(event, OTHER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(joined, "id", OPEN_ID);
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.of(joined));

    assertThatThrownBy(() -> service.cancel(user, EVENT_ID, OPEN_ID))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("없는 참여 취소는 404다")
  void cancelNotFound() {
    when(participationRepository.findById(OPEN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancel(user, EVENT_ID, OPEN_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private ZoneEvent event(ZoneEventStatus status, int successLimit) {
    ZoneEventType type =
        ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build();
    ZoneEvent created =
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("광안대교 야경 담기")
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(1440)
            .status(status)
            .baseReward(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null))
            .successLimitPerUser(successLimit)
            .build();
    ReflectionTestUtils.setField(created, "id", EVENT_ID);
    return created;
  }

  private ZoneEventAuthTarget target(ZoneEvent event) {
    return ZoneEventAuthTarget.builder()
        .event(event)
        .targetKind(ZoneEventTargetKind.PLACE)
        .placeName("광안대교 야경")
        .latitude(35.153)
        .longitude(129.118)
        .radiusM(100)
        .build();
  }
}
