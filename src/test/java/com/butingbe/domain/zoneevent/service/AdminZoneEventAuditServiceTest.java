package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventAuditServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventAuditService service;
  @Autowired private ZoneEventAuditLogRepository auditLogRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;
  private UUID actorId;

  @BeforeEach
  void setUp() {
    var savedOperator =
        userRepository.save(
            User.builder()
                .email("op-" + UUID.randomUUID() + "@example.com")
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .name(new Name("Kim", "Tester"))
                .nickname("op")
                .role(UserRole.USER)
                .build());
    actorId = savedOperator.getId();
    operator =
        new AuthenticatedUser(
            actorId, "op@example.com", "op", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    var savedUser =
        userRepository.save(
            User.builder()
                .email("user-" + UUID.randomUUID() + "@example.com")
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .name(new Name("Kim", "User"))
                .nickname("user")
                .role(UserRole.USER)
                .build());
    normalUser =
        new AuthenticatedUser(
            savedUser.getId(),
            "user@example.com",
            "user",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("운영자가 아니면 403이다")
  void nonOperatorForbidden() {
    assertThatThrownBy(() -> service.list(normalUser, null, null, null, null, null, 1, 20))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("resourceType/resourceId로 필터링한다")
  void filtersByResourceTypeAndId() {
    UUID targetId = UUID.randomUUID();
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(actorId)
            .action("PATCH_TITLE_DEF")
            .targetType("ZONE_TITLE_DEF")
            .targetId(targetId)
            .detail(Map.of("k", "v"))
            .build());
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(actorId)
            .action("CREATE_EVENT")
            .targetType("EVENT")
            .targetId(UUID.randomUUID())
            .detail(Map.of())
            .build());

    var result = service.list(operator, "ZONE_TITLE_DEF", targetId, null, null, null, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).action()).isEqualTo("PATCH_TITLE_DEF");
  }

  @Test
  @DisplayName("actorId로 필터링한다")
  void filtersByActor() {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(actorId)
            .action("CREATE_TITLE_DEF")
            .targetType("ZONE_TITLE_DEF")
            .targetId(UUID.randomUUID())
            .detail(Map.of())
            .build());
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(UUID.randomUUID())
            .action("CREATE_TITLE_DEF")
            .targetType("ZONE_TITLE_DEF")
            .targetId(UUID.randomUUID())
            .detail(Map.of())
            .build());

    var result = service.list(operator, null, null, actorId, null, null, 1, 20);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).actorId()).isEqualTo(actorId.toString());
  }

  @Test
  @DisplayName("아무 조건 없이 조회하면 최신순으로 페이징된다")
  void listsAllOrderedByCreatedAtDesc() {
    for (int i = 0; i < 3; i++) {
      auditLogRepository.save(
          ZoneEventAuditLog.builder()
              .actorId(actorId)
              .action("ACTION_" + i)
              .targetType("ZONE_TITLE_DEF")
              .targetId(UUID.randomUUID())
              .detail(Map.of())
              .build());
    }

    var result = service.list(operator, null, null, null, null, null, 1, 2);

    assertThat(result.items()).hasSize(2);
    assertThat(result.totalElements()).isEqualTo(3);
    assertThat(result.hasNext()).isTrue();
  }
}
