package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.zoneevent.dto.request.ParticipationSubmitReqDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ZoneEventSubmitServiceTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID OTHER_ID = UUID.fromString("22222222-0000-0000-0000-000000000009");
  private static final UUID PARTICIPATION_ID =
      UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final double IN_LAT = 35.1532;
  private static final double IN_LNG = 129.1182;
  private static final String FILE_KEY = "uploads/images/photo.jpg";

  @Mock private ZoneEventParticipationRepository participationRepository;
  @Mock private ZoneEventAuthTargetRepository authTargetRepository;
  @Mock private FileMetadataRepository fileMetadataRepository;
  @Mock private RewardService rewardService;
  @Mock private UserPointService userPointService;
  @Mock private com.butingbe.domain.zonetitle.service.ZoneTitleService zoneTitleService;

  private ZoneEventSubmitService service;
  private AuthenticatedUser user;
  private ZoneEvent event;

  @BeforeEach
  void setUp() {
    service =
        new ZoneEventSubmitService(
            participationRepository,
            authTargetRepository,
            fileMetadataRepository,
            rewardService,
            userPointService,
            zoneTitleService);
    ReflectionTestUtils.setField(service, "reviewMode", "AUTO");
    ReflectionTestUtils.setField(service, "capturedAtThresholdMinutes", 10L);
    user = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    event = event(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("AUTO 판정에서 제출이 통과하면 SUCCESS로 확정하고 기본 보상을 지급한다")
  void autoApproveGrantsReward() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/jpeg");
    when(rewardService.grantBaseReward(
            USER_ID, PARTICIPATION_ID, EVENT_ID, 50, "SPOT_GWANGAN_BRIDGE"))
        .thenReturn(
            new BaseRewardResult(
                List.of(
                    new GrantedRewardDto(
                        UUID.randomUUID().toString(),
                        "POINT",
                        "POINT_BASE",
                        "기본 포인트",
                        50,
                        "BASE",
                        OffsetDateTime.now())),
                350));

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY));

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(participation.getSuccess()).isTrue();
    assertThat(participation.getCompletedAt()).isNotNull();
    assertThat(participation.getMediaFileKey()).isEqualTo(FILE_KEY);
    assertThat(result.rewards()).hasSize(1);
    assertThat(result.pointBalance()).isEqualTo(350);
    assertThat(result.participation().status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("MANUAL 모드면 검수 대기로 보내고 보상을 지급하지 않는다")
  void manualModeGoesUnderReview() {
    ReflectionTestUtils.setField(service, "reviewMode", "MANUAL");
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/png");
    when(userPointService.getBalance(USER_ID)).thenReturn(100);

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY));

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.UNDER_REVIEW);
    assertThat(result.rewards()).isEmpty();
    assertThat(result.pointBalance()).isEqualTo(100);
    verify(rewardService, never()).grantBaseReward(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("촬영 시각이 임계치보다 오래되면 AUTO여도 검수 대기로 강등한다")
  void staleCapturedAtGoesUnderReview() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "image/jpeg");
    when(userPointService.getBalance(USER_ID)).thenReturn(0);
    ParticipationSubmitReqDto stale =
        new ParticipationSubmitReqDto(
            FILE_KEY, "후기", IN_LAT, IN_LNG, OffsetDateTime.now().minusMinutes(30));

    SubmitResultResDto result = service.submit(user, EVENT_ID, PARTICIPATION_ID, stale);

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.UNDER_REVIEW);
    assertThat(result.rewards()).isEmpty();
    verify(rewardService, never()).grantBaseReward(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("이미지가 아닌 미디어는 400(media.invalid)이다")
  void nonImageMediaRejected() {
    ZoneEventParticipation participation = joined();
    stubJoinedWithTargetAndMedia(participation, "application/pdf");

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.media.invalid");
  }

  @Test
  @DisplayName("등록되지 않은 fileKey는 400(media.invalid)이다")
  void unknownMediaRejected() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target()));
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.media.invalid");
  }

  @Test
  @DisplayName("제출 좌표가 반경 밖이면 400(out_of_range)이다")
  void submitOutOfRange() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target()));

    ParticipationSubmitReqDto far =
        new ParticipationSubmitReqDto(FILE_KEY, "후기", 35.16, 129.13, null);
    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, far))
        .isInstanceOf(ZoneEventOutOfRangeException.class);
  }

  @Test
  @DisplayName("JOINED가 아니면 409(invalid_state)다")
  void notJoinedRejected() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "status", ParticipationStatus.SUCCESS);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("타인의 참여 제출은 403이다")
  void otherUserForbidden() {
    ZoneEventParticipation participation = joined();
    ReflectionTestUtils.setField(participation, "userId", OTHER_ID);
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("이벤트가 ACTIVE가 아니면 409(not_active)다")
  void eventNotActive() {
    ZoneEventParticipation participation = joinedOf(event(ZoneEventStatus.CLOSED));
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.not_active");
  }

  @Test
  @DisplayName("없는 참여는 404다")
  void participationNotFound() {
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.participation.not_found");
  }

  @Test
  @DisplayName("미인증이면 401이다")
  void unauthenticated() {
    assertThatThrownBy(() -> service.submit(null, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("타인이 업로드한 미디어로 제출하면 403이다")
  void rejectsMediaUploadedByAnother() {
    ZoneEventParticipation participation = joined();
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target()));
    FileMetadata file = mock(FileMetadata.class);
    when(file.getContentType()).thenReturn("image/jpeg");
    when(file.getUploaderId()).thenReturn(UUID.randomUUID()); // 제출자(USER_ID)와 다른 업로더
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.of(file));

    assertThatThrownBy(() -> service.submit(user, EVENT_ID, PARTICIPATION_ID, request(FILE_KEY)))
        .isInstanceOf(ForbiddenException.class);
  }

  private void stubJoinedWithTargetAndMedia(
      ZoneEventParticipation participation, String contentType) {
    when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    when(authTargetRepository.findByEvent_Id(EVENT_ID)).thenReturn(Optional.of(target()));
    FileMetadata file = mock(FileMetadata.class);
    lenient().when(file.getContentType()).thenReturn(contentType);
    when(fileMetadataRepository.findByObjectKey(FILE_KEY)).thenReturn(Optional.of(file));
  }

  private ParticipationSubmitReqDto request(String fileKey) {
    return new ParticipationSubmitReqDto(fileKey, "야경 미쳤다", IN_LAT, IN_LNG, OffsetDateTime.now());
  }

  private ZoneEventParticipation joined() {
    return joinedOf(event);
  }

  private ZoneEventParticipation joinedOf(ZoneEvent event) {
    ZoneEventParticipation p = ZoneEventParticipation.join(event, USER_ID, IN_LAT, IN_LNG);
    ReflectionTestUtils.setField(p, "id", PARTICIPATION_ID);
    return p;
  }

  private ZoneEvent event(ZoneEventStatus status) {
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
            .successLimitPerUser(1)
            .build();
    ReflectionTestUtils.setField(created, "id", EVENT_ID);
    return created;
  }

  private ZoneEventAuthTarget target() {
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
