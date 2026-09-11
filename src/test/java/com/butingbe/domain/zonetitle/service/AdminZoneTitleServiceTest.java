package com.butingbe.domain.zonetitle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ConflictException;
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
class AdminZoneTitleServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneTitleService service;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private UserZoneTitleRepository userZoneTitleRepository;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;

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
}
