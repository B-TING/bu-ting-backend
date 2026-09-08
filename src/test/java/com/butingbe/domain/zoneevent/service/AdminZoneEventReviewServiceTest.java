package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.ReviewApproveReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReviewRejectReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDecisionResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
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
import com.butingbe.domain.zoneevent.repository.IdempotencyRecordRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
  @Autowired private BaseRewardPayoutRepository baseRewardPayoutRepository;
  @Autowired private RewardPayoutRepository rewardPayoutRepository;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private UserZoneTitleRepository userZoneTitleRepository;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private EntityManager entityManager;
  @MockitoBean private FileStorageService fileStorageService;

  private ZoneEvent event;
  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    Mockito.when(fileStorageService.getPresignedUrl(Mockito.anyString()))
        .thenReturn("https://signed.example/media.jpg");
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
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
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("검수 큐는 UNDER_REVIEW 참여만, roundId·eventId·zoneId로 필터링해서 돌려준다")
  void queueListsOnlyUnderReview() {
    ZoneEventParticipation underReview =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW));
    participationRepository.save(participation(ParticipationStatus.SUCCESS));

    AdminReviewQueuePageResDto queue =
        reviewService.queue(operator, null, event.getId(), event.getZoneId(), 1, 20);

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

  @Test
  @DisplayName("승인하면 SUCCESS·앨범 공개만 되고(보상 없음) 현재 제출도 SUCCESS가 된다")
  void approveMarksSuccessWithoutReward() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    titleDefRepository.save(
        ZoneTitleDef.builder()
            .titleCode(event.getZoneId() + "_T1")
            .zoneId(event.getZoneId())
            .tier(1)
            .requiredSuccessCount(1)
            .titleName(event.getZoneId() + " T1")
            .style("chip")
            .color("#000000")
            .build());

    AdminReviewDecisionResDto result =
        reviewService.approve(
            operator,
            p.getId(),
            new ReviewApproveReqDto(submission.getId(), submission.getRevision()),
            null);

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(result.status()).isEqualTo("SUCCESS");
    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.SUCCESS);
    assertThat(baseRewardPayoutRepository.count()).isZero();
    assertThat(rewardPayoutRepository.count()).isZero();
    assertThat(result.newlyAwardedTitles()).isNotEmpty();
    assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(p.getUserId())).isZero();
  }

  @Test
  @DisplayName("만료된 expectedRevision으로 승인하면 409다")
  void approveStaleRevisionConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator,
                    p.getId(),
                    new ReviewApproveReqDto(submission.getId(), submission.getRevision() + 1),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class)
        .hasMessage("error.zone_event.review.stale_revision");
  }

  @Test
  @DisplayName("참여의 currentSubmissionId가 아닌(재제출로 밀려난) submissionId로 승인하면 409다")
  void approveStaleSubmissionConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventAuthTarget target2 =
        authTargetRepository.save(
            ZoneEventAuthTarget.builder()
                .event(event)
                .targetKind(ZoneEventTargetKind.PLACE)
                .placeName("장소2")
                .latitude(35.2)
                .longitude(129.2)
                .radiusM(100)
                .build());
    ZoneEventSubmission stale =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    ZoneEventSubmission current =
        submissionRepository.save(
            ZoneEventSubmission.builder()
                .participation(p)
                .attemptNo(2)
                .target(target2)
                .placeName(target2.getPlaceName())
                .targetLatitude(target2.getLatitude())
                .targetLongitude(target2.getLongitude())
                .radiusM(target2.getRadiusM())
                .mediaFileKey("uploads/images/photo2.jpg")
                .gpsLat(35.2)
                .gpsLng(129.2)
                .capturedAt(OffsetDateTime.now())
                .build());
    p.linkSubmission(current.getId());
    participationRepository.save(p);

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator,
                    p.getId(),
                    new ReviewApproveReqDto(stale.getId(), stale.getRevision()),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class)
        .hasMessage("error.zone_event.review.stale_submission");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 재전송하면 처리를 다시 하지 않고 이전 결과를 그대로 돌려준다")
  void approveIsIdempotent() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    String key = "idem-" + UUID.randomUUID();
    // 같은 페이로드로 재전송하는 실제 클라이언트 재시도를 모사한다. submission은 이 테스트와 서비스가 같은 영속성 컨텍스트를
    // 공유하므로(같은 트랜잭션), approve() 1차 호출의 flush 이후 이 객체의 revision 필드가 실제로 증가한다 — 두 번째 요청도
    // submission.getRevision()을 다시 읽으면 실제로는 같은 재시도인데도 다른 revision을 실어 보내게 되어 fingerprint가
    // 달라지므로, 재전송 의도를 정확히 반영하기 위해 요청 DTO를 재사용한다.
    ReviewApproveReqDto request =
        new ReviewApproveReqDto(submission.getId(), submission.getRevision());

    AdminReviewDecisionResDto first = reviewService.approve(operator, p.getId(), request, key);
    AdminReviewDecisionResDto replay = reviewService.approve(operator, p.getId(), request, key);

    assertThat(replay).isEqualTo(first);
  }

  @Test
  @DisplayName("이미 승인되어 SUCCESS로 바뀐 참여를 다시 승인하면 409다(requireUnderReview 상태 가드)")
  void secondApproveAfterSuccessIsRejected() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    Long revisionSeenByBoth = submission.getRevision();

    reviewService.approve(
        operator, p.getId(), new ReviewApproveReqDto(submission.getId(), revisionSeenByBoth), null);

    // 첫 승인으로 참여는 이미 SUCCESS로 바뀌었으므로, 같은 revision을 들고 온 두 번째 요청은 revision 비교에 닿기도
    // 전에 requireUnderReview의 상태 가드(더 이상 UNDER_REVIEW가 아님)에서 막힌다.
    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator,
                    p.getId(),
                    new ReviewApproveReqDto(submission.getId(), revisionSeenByBoth),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class)
        .hasMessage("error.zone_event.participation.invalid_state");
  }

  @Test
  @DisplayName("반려하면 FAIL이 되고 사유가 남으며 제출도 REJECTED가 된다(보상 없음, 재제출 가능)")
  void rejectMarksFail() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    reviewService.reject(
        operator,
        p.getId(),
        new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", submission.getRevision()),
        null);

    ZoneEventParticipation after = participationRepository.findById(p.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(ParticipationStatus.FAIL);
    assertThat(after.getFailReason()).isEqualTo("NOT_ON_SITE");
    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getReviewStatus())
        .isEqualTo(com.butingbe.domain.zoneevent.entity.SubmissionReviewStatus.REJECTED);
  }

  @Test
  @DisplayName("만료된 expectedRevision으로 반려하면 409다")
  void rejectStaleRevisionConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);

    assertThatThrownBy(
            () ->
                reviewService.reject(
                    operator,
                    p.getId(),
                    new ReviewRejectReqDto(
                        submission.getId(), "NOT_ON_SITE", submission.getRevision() + 1),
                    null))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class)
        .hasMessage("error.zone_event.review.stale_revision");
  }

  @Test
  @DisplayName("같은 Idempotency-Key로 반려를 재전송하면 두 번째 요청은 다시 처리하지 않는다(제출 상태가 한 번만 바뀐다)")
  void rejectIsIdempotent() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    String key = "idem-" + UUID.randomUUID();
    // 같은 페이로드로 재전송하는 실제 클라이언트 재시도를 모사한다. submission은 이 테스트와 서비스가 같은 영속성 컨텍스트를
    // 공유하므로(같은 트랜잭션), reject() 1차 호출의 flush 이후 이 객체의 revision 필드가 실제로 증가한다 — 두 번째 요청도
    // submission.getRevision()을 다시 읽으면 실제로는 같은 재시도인데도 다른 revision을 실어 보내게 되어 fingerprint가
    // 달라지므로, 재전송 의도를 정확히 반영하기 위해 요청 DTO를 재사용한다.
    Long revisionBeforeReject = submission.getRevision();
    ReviewRejectReqDto request =
        new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", revisionBeforeReject);

    reviewService.reject(operator, p.getId(), request, key);
    reviewService.reject(operator, p.getId(), request, key);

    assertThat(submissionRepository.findById(submission.getId()).orElseThrow().getRevision())
        .isEqualTo(revisionBeforeReject + 1); // 딱 한 번만 처리됨
  }

  @Test
  @DisplayName("같은 Idempotency-Key를 다른 반려 사유로 재전송하면 재생이 아니라 409(idempotency_key_conflict)다")
  void rejectWithDifferentReasonConflicts() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    String key = "idem-" + UUID.randomUUID();
    Long revisionBeforeReject = submission.getRevision();

    reviewService.reject(
        operator,
        p.getId(),
        new ReviewRejectReqDto(submission.getId(), "NOT_ON_SITE", revisionBeforeReject),
        key);

    assertThatThrownBy(
            () ->
                reviewService.reject(
                    operator,
                    p.getId(),
                    new ReviewRejectReqDto(
                        submission.getId(), "BLURRY_PHOTO", revisionBeforeReject),
                    key))
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class)
        .hasMessage("error.zone_event.review.idempotency_key_conflict");
  }

  @Test
  @DisplayName("검수 큐는 roundId로도 필터링한다")
  void queueFiltersByRoundId() {
    UUID roundId = UUID.randomUUID();
    ZoneEvent roundEvent =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(event.getType())
                .roundId(roundId)
                .title("회차 이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    ZoneEventParticipation matching =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(roundEvent)
                .userId(savedUser("roundUser").getId())
                .status(ParticipationStatus.UNDER_REVIEW)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());
    participationRepository.save(
        participation(ParticipationStatus.UNDER_REVIEW)); // 다른 이벤트(roundId 없음)

    AdminReviewQueuePageResDto queue = reviewService.queue(operator, roundId, null, null, 1, 20);

    assertThat(queue.items()).hasSize(1);
    assertThat(queue.items().get(0).participationId()).isEqualTo(matching.getId().toString());
  }

  @Test
  @DisplayName("참여는 있는데 사용자가 없으면 상세 조회는 404다")
  void detailUserNotFound() {
    ZoneEventParticipation p =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(UUID.randomUUID()) // 존재하지 않는 사용자
                .status(ParticipationStatus.UNDER_REVIEW)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(OffsetDateTime.now())
                .visibility(ParticipationVisibility.PUBLIC)
                .build());

    assertThatThrownBy(() -> reviewService.detail(operator, p.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.user.not_found");
  }

  @Test
  @DisplayName("없는 참여를 승인하려 하면 404다")
  void approveParticipationNotFound() {
    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator,
                    UUID.randomUUID(),
                    new ReviewApproveReqDto(UUID.randomUUID(), 0L),
                    null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.participation.not_found");
  }

  @Test
  @DisplayName("참여에 속하지 않는 submissionId로 승인하려 하면 404다")
  void approveSubmissionNotFound() {
    ZoneEventParticipation p = underReviewWithSubmission();

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator, p.getId(), new ReviewApproveReqDto(UUID.randomUUID(), 0L), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.submission.not_found");
  }

  @Test
  @DisplayName("매뉴얼 체크 통과 후에도 실제 flush 시점에 다른 트랜잭션이 이미 revision을 올렸다면 409다(진짜 낙관적 락 충돌)")
  void approveFlushDetectsConcurrentRevisionBump() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    Long revisionSeenByCaller = submission.getRevision();

    // 이 서비스 호출이 붙잡고 있는 영속성 컨텍스트가 모르는 사이, 다른 트랜잭션이 같은 row의 revision을 이미 올렸다고 가정한다.
    // 네이티브 쿼리로 DB만 바꾸면 1차 캐시에 남아있는 submission 엔티티는 여전히 예전 revision을 들고 있으므로,
    // 매뉴얼 체크(expectedRevision)는 통과하지만 실제 saveAndFlush의 버전 체크는 실패한다.
    entityManager
        .createNativeQuery(
            "UPDATE zone_event_submission SET revision = revision + 1 WHERE submission_id ="
                + " :id")
        .setParameter("id", submission.getId())
        .executeUpdate();

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator,
                    p.getId(),
                    new ReviewApproveReqDto(submission.getId(), revisionSeenByCaller),
                    null))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_event.review.stale_revision");
  }

  @Test
  @DisplayName("저장된 재생 응답이 손상된 JSON이면 500 대신 명확한 예외를 던진다")
  void approveReplayWithCorruptedJsonThrows() {
    ZoneEventParticipation p = underReviewWithSubmission();
    ZoneEventSubmission submission =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(p.getId()).get(0);
    String key = "idem-corrupt-" + UUID.randomUUID();
    String fingerprint = p.getId() + ":" + submission.getId() + ":" + submission.getRevision();
    idempotencyRecordRepository.save(
        new IdempotencyRecord(key, "zone-event-review-approve", fingerprint, "not-a-json"));

    assertThatThrownBy(
            () ->
                reviewService.approve(
                    operator,
                    p.getId(),
                    new ReviewApproveReqDto(submission.getId(), submission.getRevision()),
                    key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to deserialize");
  }

  private ZoneEventParticipation underReviewWithSubmission() {
    User participant = savedUser("참가자");
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
    return participationRepository.save(p);
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
