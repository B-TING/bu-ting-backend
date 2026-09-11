package com.butingbe.domain.zonetitle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneTitleServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneTitleService service;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private UserZoneTitleRepository userZoneTitleRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;

  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    operator =
        new AuthenticatedUser(
            userRepository
                .save(
                    User.builder()
                        .email("op-" + UUID.randomUUID() + "@example.com")
                        .provider("google")
                        .providerId("google-" + UUID.randomUUID())
                        .name(new Name("Kim", "Tester"))
                        .nickname("op")
                        .role(UserRole.USER)
                        .build())
                .getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("정의를 생성하면 holderCount는 0이고 감사 로그가 남는다")
  void createsDefAndAudits() {
    var result =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    assertThat(result.holderCount()).isZero();
    assertThat(result.titleCode()).isEqualTo("SUYEONG_NAMGU_T1");
    assertThat(
            auditLogRepository.findByTargetTypeAndTargetId(
                "ZONE_TITLE_DEF", UUID.fromString(result.titleDefId())))
        .hasSize(1);
  }

  @Test
  @DisplayName("같은 구역·단계로 중복 생성하면 409")
  void rejectsDuplicateTier() {
    service.create(
        operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.create(
                    operator,
                    new AdminZoneTitleCreateReqDto(
                        "SUYEONG_NAMGU", 1, 5, "다른이름", "chip", "#111111")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.duplicate_tier");
  }

  @Test
  @DisplayName("같은 구역·달성 기준으로 중복 생성하면 409")
  void rejectsDuplicateRequiredSuccessCount() {
    service.create(
        operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.create(
                    operator,
                    new AdminZoneTitleCreateReqDto(
                        "SUYEONG_NAMGU", 2, 1, "다른이름", "chip", "#111111")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.duplicate_required_success_count");
  }

  @Test
  @DisplayName("높은 단계인데 달성 기준이 더 낮으면 400")
  void rejectsNonMonotonicRequiredSuccessCount() {
    service.create(
        operator, new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.create(
                    operator,
                    new AdminZoneTitleCreateReqDto(
                        "SUYEONG_NAMGU", 2, 3, "다른이름", "chip", "#111111")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_title.invalid_required_success_count");
  }

  @Test
  @DisplayName("list는 zoneId·tier 순으로 정렬되고 holderCount를 채운다")
  void listOrdersByZoneAndTierWithHolderCount() {
    ZoneTitleDef def =
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode("SUYEONG_NAMGU_T1")
                .zoneId("SUYEONG_NAMGU")
                .tier(1)
                .requiredSuccessCount(1)
                .titleName("탐방가")
                .style("chip")
                .color("#000000")
                .build());
    var holder =
        userRepository.save(
            User.builder()
                .email("h-" + UUID.randomUUID() + "@example.com")
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .name(new Name("Kim", "Holder"))
                .nickname("holder")
                .role(UserRole.USER)
                .build());
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(holder.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build());

    var result = service.list(operator);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).holderCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("update: retroactive=true면 이미 충족한 유저에게 소급 발급한다")
  void updateWithRetroactiveBackfills() {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var holder = savedUser("소급대상");
    successfulParticipation(holder.getId(), "SUYEONG_NAMGU");

    var result =
        service.update(
            operator,
            titleDefId,
            new com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto(
                null, 1, true, created.revision()));

    assertThat(result.requiredSuccessCount()).isEqualTo(1);
    assertThat(result.holderCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("update: retroactive=false면 소급 발급하지 않는다")
  void updateWithoutRetroactiveDoesNotBackfill() {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var holder = savedUser("소급대상2");
    successfulParticipation(holder.getId(), "SUYEONG_NAMGU");

    var result =
        service.update(
            operator,
            titleDefId,
            new com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto(
                null, 1, false, created.revision()));

    assertThat(result.holderCount()).isZero();
  }

  @Test
  @DisplayName("update: expectedRevision이 다르면 409")
  void updateRejectsStaleRevision() {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 5, "탐방가", "chip", "#000000"));

    assertThatThrownBy(
            () ->
                service.update(
                    operator,
                    UUID.fromString(created.titleDefId()),
                    new com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto(
                        "새이름", null, false, created.revision() + 1)))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.stale_revision");
  }

  @Test
  @DisplayName("delete: 보유자가 없으면 삭제된다")
  void deletesDefWithoutHolders() {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));

    service.delete(operator, UUID.fromString(created.titleDefId()));

    assertThat(titleDefRepository.findById(UUID.fromString(created.titleDefId()))).isEmpty();
  }

  @Test
  @DisplayName("delete: 보유자가 있으면 409")
  void deleteRejectsWhenHoldersExist() {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var holder = savedUser("보유자");
    successfulParticipation(holder.getId(), "SUYEONG_NAMGU");
    var def = titleDefRepository.findById(titleDefId).orElseThrow();
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(holder.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build());

    assertThatThrownBy(() -> service.delete(operator, titleDefId))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.zone_title.has_holders");
  }

  @Test
  @DisplayName("holders: earnedAt 내림차순으로 페이징하고 닉네임을 채운다")
  void holdersReturnsPagedItemsWithNickname() {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var def = titleDefRepository.findById(titleDefId).orElseThrow();
    var holder = savedUser("보유자1");
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(holder.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(true)
            .build());

    var result = service.holders(operator, titleDefId, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).nickname()).isEqualTo("보유자1");
    assertThat(result.items().get(0).equipped()).isTrue();
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("holders: 없는 정의면 404")
  void holdersNotFound() {
    assertThatThrownBy(() -> service.holders(operator, UUID.randomUUID(), 1, 20))
        .isInstanceOf(com.butingbe.global.error.exception.ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("holders: earnedAt 내림차순 정렬과 페이지 크기를 지킨다")
  void holdersOrdersByEarnedAtDescAndPaginates() throws InterruptedException {
    var created =
        service.create(
            operator,
            new AdminZoneTitleCreateReqDto("SUYEONG_NAMGU", 1, 1, "탐방가", "chip", "#000000"));
    UUID titleDefId = UUID.fromString(created.titleDefId());
    var def = titleDefRepository.findById(titleDefId).orElseThrow();
    var first = savedUser("먼저획득");
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(first.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build());
    Thread.sleep(5);
    var second = savedUser("나중획득");
    userZoneTitleRepository.save(
        com.butingbe.domain.zonetitle.entity.UserZoneTitle.builder()
            .userId(second.getId())
            .titleDef(def)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build());

    var page1 = service.holders(operator, titleDefId, 1, 1);

    assertThat(page1.items()).hasSize(1);
    assertThat(page1.items().get(0).nickname()).isEqualTo("나중획득");
    assertThat(page1.items().get(0).email()).isEqualTo(second.getEmail());
    assertThat(page1.totalElements()).isEqualTo(2);
    assertThat(page1.totalPages()).isEqualTo(2);
    assertThat(page1.hasNext()).isTrue();

    var page2 = service.holders(operator, titleDefId, 2, 1);

    assertThat(page2.items()).hasSize(1);
    assertThat(page2.items().get(0).nickname()).isEqualTo("먼저획득");
    assertThat(page2.hasNext()).isFalse();
  }

  private User savedUser(String nickname) {
    return userRepository.save(
        User.builder()
            .email(nickname + "-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", nickname))
            .nickname(nickname)
            .role(UserRole.USER)
            .build());
  }

  private void successfulParticipation(UUID userId, String zoneId) {
    ZoneEventType type =
        zoneEventTypeRepository.findAll().stream()
            .findFirst()
            .orElseGet(
                () ->
                    zoneEventTypeRepository.save(
                        ZoneEventType.builder()
                            .typeCode("PLACE_AUTH")
                            .name("장소 인증")
                            .requiresUpload(true)
                            .build()));
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId(zoneId)
                .type(type)
                .title("이벤트")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .successLimitPerUser(1)
                .build());
    participationRepository.save(
        ZoneEventParticipation.builder()
            .event(event)
            .userId(userId)
            .status(ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .build());
  }
}
