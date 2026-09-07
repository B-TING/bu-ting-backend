package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.ReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
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
class AdminReviewServiceTest extends AbstractContainerTest {

  @Autowired private AdminReviewService reviewService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private UserRepository userRepository;

  private ZoneEvent event;
  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;

  @BeforeEach
  void setUp() {
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
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_BASE")
            .name("포인트")
            .pointAmount(50)
            .build());
    operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    normalUser = AuthenticatedUser.from(userRepository.save(user("normal")));
  }

  @Test
  @DisplayName("검수 큐는 UNDER_REVIEW·숨김 참여를 신고 수와 함께 돌려준다")
  void reviewQueueListsUnderReviewAndHidden() {
    participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));
    ZoneEventParticipation hidden =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, true));
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(hidden.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());
    participationRepository.save(participation(ParticipationStatus.SUCCESS, false)); // 큐에 안 뜸

    ReviewQueuePageResDto queue = reviewService.reviewQueue(operator, null, 20);

    assertThat(queue.items()).hasSize(2);
    assertThat(queue.items()).anyMatch(i -> i.hidden() && i.reportCount() == 1);
  }

  @Test
  @DisplayName("운영자가 아니면 검수 큐 조회는 403이다")
  void queueForbidden() {
    assertThatThrownBy(() -> reviewService.reviewQueue(normalUser, null, 20))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("승인하면 SUCCESS로 확정되고 보상이 지급된다")
  void approveGrantsReward() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));

    SubmitResultResDto result = reviewService.approve(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.SUCCESS);
    assertThat(participationRepository.findById(p.getId()).orElseThrow().getReviewedBy())
        .isEqualTo(operator.id());
    assertThat(result.rewards()).isNotEmpty();
    assertThat(userPointService.getBalance(p.getUserId())).isEqualTo(50);
  }

  @Test
  @DisplayName("반려하면 FAIL이 되고 사유가 남는다")
  void rejectMarksFail() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));

    reviewService.reject(operator, p.getId(), "NOT_ON_SITE");

    ZoneEventParticipation after = participationRepository.findById(p.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(ParticipationStatus.FAIL);
    assertThat(after.getFailReason()).isEqualTo("NOT_ON_SITE");
  }

  @Test
  @DisplayName("회수하면 REVOKED가 되고 지급 포인트가 되돌아간다")
  void revokeReversesReward() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));
    reviewService.approve(operator, p.getId());
    assertThat(userPointService.getBalance(p.getUserId())).isEqualTo(50);

    reviewService.revoke(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getStatus())
        .isEqualTo(ParticipationStatus.REVOKED);
    assertThat(userPointService.getBalance(p.getUserId())).isZero();
  }

  @Test
  @DisplayName("숨김 해제하면 hidden이 풀리고 신고가 DISMISSED된다")
  void unhideDismissesReports() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, true));
    reportRepository.save(
        ZoneEventReport.builder()
            .participationId(p.getId())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.SPAM)
            .build());

    reviewService.unhide(operator, p.getId());

    assertThat(participationRepository.findById(p.getId()).orElseThrow().getHidden()).isFalse();
    assertThat(reportRepository.findByParticipationId(p.getId()).get(0).getStatus())
        .isEqualTo(ReportStatus.DISMISSED);
  }

  @Test
  @DisplayName("상태가 맞지 않으면 승인·반려·회수는 409다")
  void invalidStateTransitions() {
    ZoneEventParticipation success =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    assertThatThrownBy(() -> reviewService.approve(operator, success.getId()))
        .isInstanceOf(ConflictException.class);
    ZoneEventParticipation joined =
        participationRepository.save(participation(ParticipationStatus.JOINED, false));
    assertThatThrownBy(() -> reviewService.revoke(operator, joined.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("검수 큐를 커서 페이징하고 기본 크기·잘못된 커서를 처리한다")
  void queueCursorPagingAndEdges() {
    for (int i = 0; i < 3; i++) {
      participationRepository.save(participation(ParticipationStatus.UNDER_REVIEW, false));
    }
    ReviewQueuePageResDto first = reviewService.reviewQueue(operator, null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();
    ReviewQueuePageResDto second = reviewService.reviewQueue(operator, first.nextCursor(), 2);
    assertThat(second.items()).hasSize(1);

    assertThat(reviewService.reviewQueue(operator, null, null).items()).hasSize(3); // 기본 크기
    assertThatThrownBy(() -> reviewService.reviewQueue(operator, "!!bad!!", 20))
        .isInstanceOf(IllegalArgumentException.class);
    String noPipe =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("nopipe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(() -> reviewService.reviewQueue(operator, noPipe, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("없는 참여 승인·숨김해제는 404다")
  void notFound() {
    assertThatThrownBy(() -> reviewService.approve(operator, UUID.randomUUID()))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
    assertThatThrownBy(() -> reviewService.unhide(operator, UUID.randomUUID()))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
  }

  private ZoneEventParticipation participation(ParticipationStatus status, boolean hidden) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(savedUser("p").getId())
            .status(status)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    if (hidden) {
      ReflectionTestUtils.setField(p, "hidden", true);
    }
    if (status == ParticipationStatus.SUCCESS) {
      ReflectionTestUtils.setField(p, "completedAt", OffsetDateTime.now());
    }
    return p;
  }

  private User savedUser(String nick) {
    return userRepository.save(user(nick));
  }

  private User user(String nick) {
    return User.builder()
        .email(nick + "-" + UUID.randomUUID() + "@example.com")
        .provider("google")
        .providerId("google-" + UUID.randomUUID())
        .name(new Name("Kim", "Tester"))
        .nickname(nick)
        .role(UserRole.USER)
        .build();
  }
}
