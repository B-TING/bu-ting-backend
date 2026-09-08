package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.reward.service.UserPointService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
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
class AdminReviewServiceTest extends AbstractContainerTest {

  @Autowired private AdminReviewService reviewService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventReportRepository reportRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private RewardService rewardService;
  @Autowired private UserRepository userRepository;

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
  }

  @Test
  @DisplayName("회수하면 REVOKED가 되고 지급 포인트가 되돌아간다")
  void revokeReversesReward() {
    ZoneEventParticipation p =
        participationRepository.save(participation(ParticipationStatus.SUCCESS, false));
    rewardService.grantBaseReward(p.getUserId(), p.getId(), event.getId(), 50, null);
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
  @DisplayName("상태가 맞지 않으면 회수는 409다")
  void invalidStateTransitions() {
    ZoneEventParticipation joined =
        participationRepository.save(participation(ParticipationStatus.JOINED, false));
    assertThatThrownBy(() -> reviewService.revoke(operator, joined.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("없는 참여 회수·숨김해제는 404다")
  void notFound() {
    assertThatThrownBy(() -> reviewService.revoke(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> reviewService.unhide(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
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
