package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.entity.FileMetadata;
import com.butingbe.domain.file.repository.FileMetadataRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ForbiddenException;
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
class AdminZoneEventReviewServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventReviewService reviewService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ZoneEventAuthTargetRepository authTargetRepository;
  @Autowired private ZoneEventSubmissionRepository submissionRepository;
  @Autowired private FileMetadataRepository fileMetadataRepository;

  private ZoneEvent event;
  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("PLACE_AUTH").name("장소 인증").requiresUpload(true).build());
    event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    operator =
        new AuthenticatedUser(
            savedUser("op").getId(), "op@example.com", "op", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("검수 큐는 UNDER_REVIEW 참여만, roundId·eventId·zoneId로 필터링해서 돌려준다")
  void queueListsOnlyUnderReview() {
    ZoneEventParticipation underReview = participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW));
    participationRepository.save(participation(ParticipationStatus.SUCCESS));

    AdminReviewQueuePageResDto queue = reviewService.queue(operator, null, event.getId(), event.getZoneId(), 1, 20);

    assertThat(queue.items()).hasSize(1);
    assertThat(queue.items().get(0).participationId()).isEqualTo(underReview.getId().toString());
    assertThat(queue.page()).isEqualTo(1);
  }

  @Test
  @DisplayName("운영자가 아니면 검수 큐 조회는 403이다")
  void queueForbidden() {
    AuthenticatedUser normalUser =
        AuthenticatedUser.from(
            userRepository.save(
                User.builder()
                    .email("normal-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Tester"))
                    .nickname("normal")
                    .role(UserRole.USER)
                    .build()));
    assertThatThrownBy(() -> reviewService.queue(normalUser, null, null, null, 1, 20))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("상세 조회는 사용자 표시 정보·현재 제출·전체 제출 이력·타겟/보상 스냅샷을 담는다")
  void detailReturnsFullPicture() {
    User participant = savedUser("참가자닉");
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(participant.getId())
                .status(ParticipationStatus.UNDER_REVIEW)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    ZoneEventAuthTarget target =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeName("장소")
                .latitude(35.1)
                .longitude(129.1)
                .radiusM(100)
                .build());
    fileMetadataRepository.save(
        FileMetadata.builder()
            .objectKey("uploads/images/photo.jpg")
            .originalFileName("photo.jpg")
            .contentType("image/jpeg")
            .mediaType("IMAGE")
            .fileSize(1024L)
            .bucket("buting-private")
            .build());
    ZoneEventSubmission submission =
        submissionRepository.save(
            ZoneEventSubmission.builder()
                .participation(p)
                .attemptNo(1)
                .target(target)
                .placeName(target.getPlaceName())
                .targetLatitude(target.getLatitude())
                .targetLongitude(target.getLongitude())
                .radiusM(target.getRadiusM())
                .mediaFileKey("uploads/images/photo.jpg")
                .gpsLat(35.1)
                .gpsLng(129.1)
                .capturedAt(OffsetDateTime.now())
                .build());
    ReflectionTestUtils.setField(p, "currentSubmissionId", submission.getId());
    participationRepository.save(p);

    AdminReviewDetailResDto detail = reviewService.detail(operator, p.getId());

    assertThat(detail.userNickname()).isEqualTo("참가자닉");
    assertThat(detail.currentSubmission().submissionId()).isEqualTo(submission.getId().toString());
    assertThat(detail.submissionHistory()).hasSize(1);
    assertThat(detail.rewardSnapshot().points()).isEqualTo(50);
    assertThat(detail.currentSubmission().placeName()).isEqualTo("장소");
  }

  @Test
  @DisplayName("없는 참여 상세 조회는 404다")
  void detailNotFound() {
    assertThatThrownBy(() -> reviewService.detail(operator, UUID.randomUUID()))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
  }

  private ZoneEventParticipation participation(ParticipationStatus status) {
    return participationRepository.save(
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser("p").getId())
            .status(status)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build());
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
