package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.AlbumPageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventLike;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventLikeRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventAlbumServiceTest extends AbstractContainerTest {

  @Autowired private ZoneEventAlbumService albumService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventLikeRepository likeRepository;
  @Autowired private UserRepository userRepository;
  @MockitoBean private FileStorageService fileStorageService;

  private ZoneEvent event;
  private UUID authorId;

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
                .title("광안대교 야경")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    authorId = savedUser("author").getId();
  }

  @Test
  @DisplayName("공개 성공 참여만 최신순으로 앨범에 보인다")
  void eventAlbumShowsPublicSuccess() {
    success(authorId, 0, ParticipationVisibility.PUBLIC, "photo1.jpg");
    success(savedUser("private").getId(), 0, ParticipationVisibility.PRIVATE, "photo2.jpg");
    joinedOnly(savedUser("joined").getId());

    AlbumPageResDto album = albumService.eventAlbum(event.getId(), "LATEST", null, 20, null);

    assertThat(album.items()).hasSize(1);
    assertThat(album.items().get(0).authorNickname()).isEqualTo("author");
    assertThat(album.items().get(0).zoneId()).isEqualTo("SUYEONG_NAMGU");
    assertThat(album.items().get(0).mediaUrl()).isNotNull();
    assertThat(album.items().get(0).likedByMe()).isFalse();
    assertThat(album.items().get(0).isMine()).isFalse();
  }

  @Test
  @DisplayName("좋아요순 정렬과 커서 페이징이 동작한다")
  void mostLikedSortAndPaging() {
    success(authorId, 5, ParticipationVisibility.PUBLIC, "a.jpg");
    success(savedUser("b").getId(), 10, ParticipationVisibility.PUBLIC, "b.jpg");
    success(savedUser("c").getId(), 1, ParticipationVisibility.PUBLIC, "c.jpg");

    AlbumPageResDto first = albumService.eventAlbum(event.getId(), "MOST_LIKED", null, 2, null);
    assertThat(first.items()).hasSize(2);
    assertThat(first.items().get(0).likeCount()).isEqualTo(10);
    assertThat(first.items().get(1).likeCount()).isEqualTo(5);
    assertThat(first.hasNext()).isTrue();

    AlbumPageResDto second =
        albumService.eventAlbum(event.getId(), "MOST_LIKED", first.nextCursor(), 2, null);
    assertThat(second.items()).hasSize(1);
    assertThat(second.items().get(0).likeCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("로그인 뷰어의 좋아요·소유 여부를 채운다")
  void personalisedFields() {
    ZoneEventParticipation mine = success(authorId, 0, ParticipationVisibility.PUBLIC, "m.jpg");
    likeRepository.save(
        ZoneEventLike.builder().participationId(mine.getId()).userId(authorId).build());

    AlbumPageResDto album = albumService.eventAlbum(event.getId(), "LATEST", null, 20, authorId);

    assertThat(album.items().get(0).likedByMe()).isTrue();
    assertThat(album.items().get(0).isMine()).isTrue();
  }

  @Test
  @DisplayName("구역·회차 앨범도 같은 형태로 조회한다")
  void zoneAndRoundAlbum() {
    UUID roundId = UUID.randomUUID();
    ReflectionTestUtils.setField(event, "roundId", roundId);
    success(authorId, 0, ParticipationVisibility.PUBLIC, "z.jpg");

    assertThat(albumService.zoneAlbum("SUYEONG_NAMGU", null, null, 20, null).items()).hasSize(1);
    assertThat(albumService.roundAlbum(roundId, null, null, 20, null).items()).hasSize(1);
  }

  @Test
  @DisplayName("공개 설정을 PRIVATE로 바꾸면 앨범에서 빠진다")
  void setVisibilityRemovesFromAlbum() {
    ZoneEventParticipation p = success(authorId, 0, ParticipationVisibility.PUBLIC, "v.jpg");
    AuthenticatedUser author =
        new AuthenticatedUser(authorId, "a@example.com", "author", List.of());

    albumService.setVisibility(author, p.getId(), "PRIVATE");

    assertThat(albumService.eventAlbum(event.getId(), "LATEST", null, 20, null).items()).isEmpty();
  }

  @Test
  @DisplayName("본인 아님·SUCCESS 아님 공개 설정은 403·409다")
  void setVisibilityGuards() {
    ZoneEventParticipation success = success(authorId, 0, ParticipationVisibility.PUBLIC, "g.jpg");
    AuthenticatedUser other =
        new AuthenticatedUser(savedUser("other").getId(), "o@example.com", "other", List.of());
    assertThatThrownBy(() -> albumService.setVisibility(other, success.getId(), "PRIVATE"))
        .isInstanceOf(ForbiddenException.class);

    ZoneEventParticipation joined =
        participationRepository.save(ZoneEventParticipation.join(event, authorId, 35.1, 129.1));
    AuthenticatedUser author =
        new AuthenticatedUser(authorId, "a@example.com", "author", List.of());
    assertThatThrownBy(() -> albumService.setVisibility(author, joined.getId(), "PRIVATE"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("최신순 커서 페이징과 기본 페이지 크기가 동작한다")
  void latestCursorPagingAndDefaultSize() {
    success(authorId, 0, ParticipationVisibility.PUBLIC, "a.jpg");
    success(savedUser("b2").getId(), 0, ParticipationVisibility.PUBLIC, "b.jpg");
    success(savedUser("c2").getId(), 0, ParticipationVisibility.PUBLIC, "c.jpg");

    AlbumPageResDto first = albumService.eventAlbum(event.getId(), "LATEST", null, 2, null);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();
    AlbumPageResDto second =
        albumService.eventAlbum(event.getId(), "LATEST", first.nextCursor(), 2, null);
    assertThat(second.items()).hasSize(1);

    // size null → 기본 크기
    assertThat(albumService.eventAlbum(event.getId(), null, null, null, null).items()).hasSize(3);
  }

  @Test
  @DisplayName("미디어 키가 없는 성공 참여는 mediaUrl이 null이다")
  void nullMediaUrlWhenNoKey() {
    ZoneEventParticipation p = ZoneEventParticipation.join(event, authorId, 35.1, 129.1);
    ReflectionTestUtils.setField(p, "status", ParticipationStatus.SUCCESS);
    ReflectionTestUtils.setField(p, "completedAt", OffsetDateTime.now());
    participationRepository.save(p);

    AlbumPageResDto album = albumService.eventAlbum(event.getId(), "LATEST", null, 20, null);
    assertThat(album.items().get(0).mediaUrl()).isNull();
    assertThat(album.items().get(0).mediaUrlExpiresIn()).isNull();
  }

  @Test
  @DisplayName("공개 설정: 없는 참여 404, 미인증 401, 잘못된 값 400")
  void setVisibilityErrorPaths() {
    AuthenticatedUser author =
        new AuthenticatedUser(authorId, "a@example.com", "author", List.of());
    assertThatThrownBy(() -> albumService.setVisibility(author, UUID.randomUUID(), "PRIVATE"))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
    assertThatThrownBy(() -> albumService.setVisibility(null, UUID.randomUUID(), "PRIVATE"))
        .isInstanceOf(com.butingbe.global.error.exception.UnauthenticatedException.class);

    ZoneEventParticipation p = success(authorId, 0, ParticipationVisibility.PUBLIC, "x.jpg");
    assertThatThrownBy(() -> albumService.setVisibility(author, p.getId(), "WEIRD"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("잘못된 구역·정렬·커서(형식 포함)는 400이다")
  void invalidInputs() {
    assertThatThrownBy(() -> albumService.zoneAlbum("NOWHERE", null, null, 20, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> albumService.eventAlbum(event.getId(), "WEIRD", null, 20, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> albumService.eventAlbum(event.getId(), "LATEST", "!!bad!!", 20, null))
        .isInstanceOf(IllegalArgumentException.class);

    // 형식은 맞지만 구성 요소 수가 틀린 커서
    String latestWrongParts = base64("a|b|c");
    assertThatThrownBy(
            () -> albumService.eventAlbum(event.getId(), "LATEST", latestWrongParts, 20, null))
        .isInstanceOf(IllegalArgumentException.class);
    String mostLikedWrongParts = base64("1|2");
    assertThatThrownBy(
            () ->
                albumService.eventAlbum(event.getId(), "MOST_LIKED", mostLikedWrongParts, 20, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private String base64(String raw) {
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private ZoneEventParticipation success(
      UUID userId, long likeCount, ParticipationVisibility visibility, String fileKey) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(userId)
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(visibility)
            .build();
    p.submit(fileKey, "후기", 35.1, 129.1, OffsetDateTime.now());
    p.markSuccess();
    ReflectionTestUtils.setField(p, "likeCount", likeCount);
    return participationRepository.save(p);
  }

  private void joinedOnly(UUID userId) {
    participationRepository.save(ZoneEventParticipation.join(event, userId, 35.1, 129.1));
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
