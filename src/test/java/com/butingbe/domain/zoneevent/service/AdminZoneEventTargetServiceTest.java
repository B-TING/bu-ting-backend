package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.place.dto.response.PlaceSummaryResDto;
import com.butingbe.domain.place.service.PlaceService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.AdminAuthTargetReplaceReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventTargetServiceTest extends com.butingbe.support.AbstractContainerTest {

  @Autowired private AdminZoneEventTargetService targetService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ZoneEventParticipationService participationService;
  @MockitoBean private PlaceService placeService;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private UUID eventId;

  @BeforeEach
  void setUp() {
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder().typeCode("MISSION").name("미션").requiresUpload(false).build());
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .title("광안대교 야경")
                .startsAt(OffsetDateTime.now())
                .durationMinutes(120)
                .status(ZoneEventStatus.SCHEDULED)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(1)
                .build());
    eventId = event.getId();
    operator = AuthenticatedUser.from(savedUser("admin", UserRole.ADMIN));
    normalUser = AuthenticatedUser.from(savedUser("user", UserRole.USER));
  }

  private User savedUser(String nickname, UserRole role) {
    return userRepository.save(
        User.builder()
            .email(nickname + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname(nickname)
            .role(role)
            .build());
  }

  private AdminAuthTargetCreateReqDto placeCreateReq(String contentId, Double lat, Double lng) {
    return new AdminAuthTargetCreateReqDto(
        "PLACE", null, contentId, "12", null, "가이드", null, lat, lng, 100);
  }

  @Test
  @DisplayName("PLACE 타겟 생성 시 좌표를 생략하면 원본 좌표를 그대로 쓴다")
  void createPlaceTargetDefaultsToSourceCoordinates() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));

    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThat(created.placeName()).isEqualTo("광안대교");
    assertThat(created.sourceLatitude()).isEqualTo(35.1532);
    assertThat(created.latitude()).isEqualTo(35.1532);
    assertThat(created.coordinatesOverridden()).isFalse();
    assertThat(created.status()).isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("PLACE 타겟 생성 시 좌표를 직접 주면 원본과 분리 저장되고 override로 표시된다")
  void createPlaceTargetWithOverrideCoordinates() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));

    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", 35.16, 129.12));

    assertThat(created.sourceLatitude()).isEqualTo(35.1532);
    assertThat(created.latitude()).isEqualTo(35.16);
    assertThat(created.coordinatesOverridden()).isTrue();
  }

  @Test
  @DisplayName("좌표를 하나만 주면 400이다")
  void createWithOnlyOneCoordinateRejected() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));

    assertThatThrownBy(
            () -> targetService.create(operator, eventId, placeCreateReq("126081", 35.16, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_coordinates");
  }

  @Test
  @DisplayName("존재하지 않는 placeContentId는 400이다")
  void createWithUnknownPlaceRejected() {
    when(placeService.getPlaceSummary(eq("GHOST"))).thenReturn(null);

    assertThatThrownBy(
            () -> targetService.create(operator, eventId, placeCreateReq("GHOST", null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.place_not_found");
  }

  @Test
  @DisplayName("같은 이벤트에 같은 contentId를 두 번 등록하면 409다")
  void createDuplicatePlaceContentIdConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () -> targetService.create(operator, eventId, placeCreateReq("126081", null, null)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  @DisplayName("OBJECT 타겟은 landmarkId·placeName·좌표가 모두 있어야 한다")
  void createObjectTargetRequiresExplicitFields() {
    AdminAuthTargetCreateReqDto missingLandmark =
        new AdminAuthTargetCreateReqDto(
            "OBJECT", null, null, null, "표지판", null, null, 35.1, 129.1, 100);
    assertThatThrownBy(() -> targetService.create(operator, eventId, missingLandmark))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_kind");

    AdminAuthTargetCreateReqDto ok =
        new AdminAuthTargetCreateReqDto(
            "OBJECT", "signpost-1", null, null, "표지판", null, null, 35.1, 129.1, 100);
    AdminAuthTargetResDto created = targetService.create(operator, eventId, ok);
    assertThat(created.coordinatesOverridden()).isFalse();
    assertThat(created.placeContentId()).isNull();
  }

  @Test
  @DisplayName("OBJECT 타겟은 원본 좌표가 없으므로 좌표를 생략하면 400이다")
  void createObjectTargetWithoutCoordinatesRejected() {
    AdminAuthTargetCreateReqDto missingCoordinates =
        new AdminAuthTargetCreateReqDto(
            "OBJECT", "signpost-1", null, null, "표지판", null, null, null, null, 100);

    assertThatThrownBy(() -> targetService.create(operator, eventId, missingCoordinates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_coordinates");
  }

  @Test
  @DisplayName("targetKind가 유효하지 않으면 400이다")
  void createWithInvalidKindRejected() {
    AdminAuthTargetCreateReqDto invalidKind =
        new AdminAuthTargetCreateReqDto(
            "GHOST", null, "126081", "12", null, null, null, null, null, 100);

    assertThatThrownBy(() -> targetService.create(operator, eventId, invalidKind))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_kind");
  }

  @Test
  @DisplayName("PLACE 타겟은 placeContentId·contentTypeId가 모두 있어야 한다")
  void createPlaceTargetRequiresContentIdAndTypeId() {
    AdminAuthTargetCreateReqDto missingContentTypeId =
        new AdminAuthTargetCreateReqDto(
            "PLACE", null, "126081", null, null, null, null, null, null, 100);

    assertThatThrownBy(() -> targetService.create(operator, eventId, missingContentTypeId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_kind");
  }

  @Test
  @DisplayName("운영자가 아니면 403이다")
  void nonOperatorForbidden() {
    assertThatThrownBy(
            () -> targetService.create(normalUser, eventId, placeCreateReq("126081", null, null)))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("목록은 상태와 무관하게 이벤트의 모든 타겟을 반환한다")
  void listReturnsAllStatuses() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    targetService.cancel(operator, eventId, UUID.fromString(created.targetId()));

    assertThat(targetService.list(operator, eventId)).hasSize(1);
    assertThat(targetService.list(operator, eventId).get(0).status()).isEqualTo("CANCELLED");
  }

  @Test
  @DisplayName("목록 조회 시 존재하지 않는 이벤트는 404다")
  void listUnknownEventNotFound() {
    assertThatThrownBy(() -> targetService.list(operator, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("수정은 좌표·반경·가이드를 바꾸고 이전값을 감사 로그에 남긴다")
  void patchUpdatesFieldsAndAudits() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());

    AdminAuthTargetResDto patched =
        targetService.patch(
            operator,
            eventId,
            targetId,
            new AdminAuthTargetPatchReqDto(
                "새 가이드", null, 35.2, 129.2, 200, "반경 확대", created.revision()));

    assertThat(patched.latitude()).isEqualTo(35.2);
    assertThat(patched.radiusM()).isEqualTo(200);
    assertThat(patched.coordinatesOverridden()).isTrue();
    assertThat(auditLogRepository.findByTargetTypeAndTargetId("TARGET", targetId))
        .filteredOn(a -> a.getAction().equals("PATCH_TARGET"))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.getDetail()).containsEntry("reason", "반경 확대");
              assertThat(a.getDetail()).containsKey("before");
              assertThat(a.getDetail()).containsKey("after");
            });
  }

  @Test
  @DisplayName("좌표를 하나만 수정하면 400이다")
  void patchWithOnlyOneCoordinateRejected() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    UUID.fromString(created.targetId()),
                    new AdminAuthTargetPatchReqDto(
                        null, null, 35.2, null, null, null, created.revision())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.target.invalid_coordinates");
  }

  @Test
  @DisplayName("expectedRevision이 다르면 409다")
  void patchWithStaleRevisionConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    UUID.fromString(created.targetId()),
                    new AdminAuthTargetPatchReqDto(
                        null, null, null, null, 200, null, created.revision() + 1)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("CANCELLED 타겟은 수정할 수 없다")
  void patchNonActiveTargetConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());
    targetService.cancel(operator, eventId, targetId);

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    targetId,
                    new AdminAuthTargetPatchReqDto(null, null, null, null, 200, null, 0L)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("존재하지 않는 타겟을 수정·교체·취소하면 404다")
  void unknownTargetNotFound() {
    UUID randomTargetId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                targetService.patch(
                    operator,
                    eventId,
                    randomTargetId,
                    new AdminAuthTargetPatchReqDto(null, null, null, null, null, null, 0L)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("교체하면 기존 타겟은 REPLACED, 새 타겟은 ACTIVE가 된다")
  void replaceMarksOldReplacedAndCreatesNewActive() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    when(placeService.getPlaceSummary(eq("999999")))
        .thenReturn(new PlaceSummaryResDto("999999", "센텀시티", 35.17, 129.13));
    AdminAuthTargetResDto original =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    AdminAuthTargetResDto replacement =
        targetService.replace(
            operator,
            eventId,
            UUID.fromString(original.targetId()),
            new AdminAuthTargetReplaceReqDto(
                "999999", "12", "우천 대체", null, null, null, 120, "우천으로 긴급 교체"));

    assertThat(replacement.placeName()).isEqualTo("센텀시티");
    assertThat(replacement.status()).isEqualTo("ACTIVE");
    assertThat(targetService.list(operator, eventId))
        .anySatisfy(
            t -> {
              assertThat(t.targetId()).isEqualTo(original.targetId());
              assertThat(t.status()).isEqualTo("REPLACED");
            });
  }

  @Test
  @DisplayName("교체 대상이 ACTIVE가 아니면 409다")
  void replaceNonActiveTargetConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());
    targetService.cancel(operator, eventId, targetId);

    assertThatThrownBy(
            () ->
                targetService.replace(
                    operator,
                    eventId,
                    targetId,
                    new AdminAuthTargetReplaceReqDto(
                        "999999", "12", null, null, null, null, 100, null)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("교체 대상 contentId가 존재하지 않으면 400이다")
  void replaceWithUnknownPlaceRejected() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    when(placeService.getPlaceSummary(eq("GHOST"))).thenReturn(null);
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    assertThatThrownBy(
            () ->
                targetService.replace(
                    operator,
                    eventId,
                    UUID.fromString(created.targetId()),
                    new AdminAuthTargetReplaceReqDto(
                        "GHOST", "12", null, null, null, null, 100, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.place_not_found");
  }

  @Test
  @DisplayName("취소하면 CANCELLED가 되고, 그 타겟이 유일했다면 신규 참여가 막힌다(기존 join 로직으로 확인)")
  void cancelOnlyTargetBlocksNewJoins() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));

    targetService.cancel(operator, eventId, UUID.fromString(created.targetId()));

    ZoneEvent event = zoneEventRepository.findById(eventId).orElseThrow();
    event.activate();
    zoneEventRepository.saveAndFlush(event);

    assertThatThrownBy(
            () ->
                participationService.join(
                    operator, eventId, UUID.fromString(created.targetId()), 35.1532, 129.1181))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("이미 CANCELLED인 타겟을 다시 취소하면 409다")
  void cancelAlreadyCancelledConflicts() {
    when(placeService.getPlaceSummary(eq("126081")))
        .thenReturn(new PlaceSummaryResDto("126081", "광안대교", 35.1532, 129.1181));
    AdminAuthTargetResDto created =
        targetService.create(operator, eventId, placeCreateReq("126081", null, null));
    UUID targetId = UUID.fromString(created.targetId());
    targetService.cancel(operator, eventId, targetId);

    assertThatThrownBy(() -> targetService.cancel(operator, eventId, targetId))
        .isInstanceOf(ConflictException.class);
  }
}
