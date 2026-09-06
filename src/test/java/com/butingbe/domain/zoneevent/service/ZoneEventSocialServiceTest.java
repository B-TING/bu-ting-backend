package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.CommentPageResDto;
import com.butingbe.domain.zoneevent.dto.response.CommentResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventSocialServiceTest extends AbstractContainerTest {

  @Autowired private ZoneEventSocialService socialService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private UserRepository userRepository;

  private ZoneEvent event;
  private UUID authorId;
  private AuthenticatedUser viewer;

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
                .title("광안대교 야경")
                .startsAt(java.time.OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    authorId = savedUser("author").getId();
    viewer = user(savedUser("viewer").getId());
  }

  @Test
  @DisplayName("좋아요를 누르면 카운터가 오르고, 취소하면 내려간다")
  void likeAndUnlike() {
    UUID participationId = publicSuccess().getId();

    socialService.like(viewer, participationId);
    assertThat(participationRepository.findById(participationId).orElseThrow().getLikeCount())
        .isEqualTo(1);

    socialService.unlike(viewer, participationId);
    assertThat(participationRepository.findById(participationId).orElseThrow().getLikeCount())
        .isZero();
  }

  @Test
  @DisplayName("자기 참여 좋아요는 400, 중복 좋아요는 409다")
  void likeGuards() {
    UUID participationId = publicSuccess().getId();
    AuthenticatedUser author = user(authorId);
    assertThatThrownBy(() -> socialService.like(author, participationId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.like.self");

    socialService.like(viewer, participationId);
    assertThatThrownBy(() -> socialService.like(viewer, participationId))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("공개·성공이 아닌 참여에는 상호작용할 수 없다(409)")
  void interactionOnlyOnPublicSuccess() {
    ZoneEventParticipation joined =
        participationRepository.save(ZoneEventParticipation.join(event, authorId, 35.1, 129.1));
    assertThatThrownBy(() -> socialService.like(viewer, joined.getId()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("댓글을 작성·조회·수정·삭제하고 카운터를 유지한다")
  void commentLifecycle() {
    UUID participationId = publicSuccess().getId();

    CommentResDto created = socialService.addComment(viewer, participationId, "멋져요");
    assertThat(participationRepository.findById(participationId).orElseThrow().getCommentCount())
        .isEqualTo(1);

    CommentPageResDto page = socialService.getComments(participationId, null, 20);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).authorNickname()).isEqualTo("viewer");

    UUID commentId = UUID.fromString(created.commentId());
    CommentResDto edited = socialService.editComment(viewer, commentId, "수정함");
    assertThat(edited.content()).isEqualTo("수정함");

    socialService.deleteComment(viewer, commentId);
    assertThat(socialService.getComments(participationId, null, 20).items()).isEmpty();
    assertThat(participationRepository.findById(participationId).orElseThrow().getCommentCount())
        .isZero();
  }

  @Test
  @DisplayName("타인 댓글은 작성자만 수정·삭제할 수 있고 운영자도 삭제할 수 있다")
  void commentEditPermissions() {
    UUID participationId = publicSuccess().getId();
    UUID commentId =
        UUID.fromString(socialService.addComment(viewer, participationId, "댓글").commentId());
    AuthenticatedUser stranger = user(savedUser("stranger").getId());
    assertThatThrownBy(() -> socialService.editComment(stranger, commentId, "몰래 수정"))
        .isInstanceOf(ForbiddenException.class);

    AuthenticatedUser operator =
        new AuthenticatedUser(
            savedUser("op").getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    socialService.deleteComment(operator, commentId); // 운영자 삭제 허용
    assertThat(socialService.getComments(participationId, null, 20).items()).isEmpty();
  }

  @Test
  @DisplayName("신고가 임계치만큼 쌓이면 참여가 자동 숨김된다")
  void reportAutoHide() {
    UUID participationId = publicSuccess().getId();
    socialService.report(user(savedUser("r1").getId()), participationId, "SPAM", null);
    socialService.report(user(savedUser("r2").getId()), participationId, "NOT_ON_SITE", "숙소");
    assertThat(participationRepository.findById(participationId).orElseThrow().getHidden())
        .isFalse();

    socialService.report(user(savedUser("r3").getId()), participationId, "INAPPROPRIATE", null);
    assertThat(participationRepository.findById(participationId).orElseThrow().getHidden())
        .isTrue();
  }

  @Test
  @DisplayName("자기 참여 신고는 400, 중복 신고는 409다")
  void reportGuards() {
    UUID participationId = publicSuccess().getId();
    assertThatThrownBy(() -> socialService.report(user(authorId), participationId, "SPAM", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.report.self");

    socialService.report(viewer, participationId, "SPAM", null);
    assertThatThrownBy(() -> socialService.report(viewer, participationId, "SPAM", null))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  @DisplayName("댓글을 커서 페이징하고 기본 크기로도 조회한다")
  void commentCursorPaging() {
    UUID participationId = publicSuccess().getId();
    socialService.addComment(user(savedUser("c1").getId()), participationId, "1");
    socialService.addComment(user(savedUser("c2").getId()), participationId, "2");
    socialService.addComment(user(savedUser("c3").getId()), participationId, "3");

    CommentPageResDto first = socialService.getComments(participationId, null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();
    CommentPageResDto second = socialService.getComments(participationId, first.nextCursor(), 2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.hasNext()).isFalse();

    // size null → 기본 크기
    assertThat(socialService.getComments(participationId, null, null).items()).hasSize(3);
  }

  @Test
  @DisplayName("없는 참여·댓글, 잘못된 신고 사유·커서, 미인증을 처리한다")
  void errorPaths() {
    assertThatThrownBy(() -> socialService.like(viewer, UUID.randomUUID()))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
    assertThatThrownBy(() -> socialService.unlike(viewer, UUID.randomUUID()))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);

    UUID participationId = publicSuccess().getId();
    UUID commentId =
        UUID.fromString(socialService.addComment(viewer, participationId, "댓글").commentId());
    socialService.deleteComment(viewer, commentId);
    assertThatThrownBy(() -> socialService.editComment(viewer, commentId, "다시"))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);

    assertThatThrownBy(() -> socialService.report(viewer, participationId, "GHOST", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> socialService.getComments(participationId, "!!bad!!", 20))
        .isInstanceOf(IllegalArgumentException.class);
    String wrongParts =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("nopipe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(() -> socialService.getComments(participationId, wrongParts, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> socialService.like(null, participationId))
        .isInstanceOf(com.butingbe.global.error.exception.UnauthenticatedException.class);
  }

  private ZoneEventParticipation publicSuccess() {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(authorId)
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(java.time.OffsetDateTime.now())
            .visibility(ParticipationVisibility.PUBLIC)
            .build();
    p.submit("m.jpg", "후기", 35.1, 129.1, java.time.OffsetDateTime.now());
    p.markSuccess();
    return participationRepository.save(p);
  }

  private AuthenticatedUser user(UUID id) {
    return new AuthenticatedUser(id, id + "@example.com", "u", List.of());
  }

  private User savedUser(String nickname) {
    return userRepository.save(
        User.builder()
            .email(nickname + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nickname)
            .role(UserRole.USER)
            .build());
  }
}
