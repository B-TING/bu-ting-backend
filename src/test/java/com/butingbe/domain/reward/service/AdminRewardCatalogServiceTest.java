package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.request.AdminRewardCatalogCreateReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardCatalogUpdateReqDto;
import com.butingbe.domain.reward.dto.response.AdminRewardGrantPageResDto;
import com.butingbe.domain.reward.dto.response.RewardCatalogResDto;
import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminRewardCatalogServiceTest extends AbstractContainerTest {

  @Autowired private AdminRewardCatalogService adminRewardCatalogService;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;
  private AuthenticatedUser normalUser;

  @BeforeEach
  void setUp() {
    operator = AuthenticatedUser.from(savedUser("admin", UserRole.ADMIN));
    normalUser = AuthenticatedUser.from(savedUser("user", UserRole.USER));
  }

  @Test
  @DisplayName("운영자가 카탈로그를 생성하고 code 중복은 409다")
  void createAndDuplicate() {
    RewardCatalogResDto created =
        adminRewardCatalogService.create(operator, createRequest("COUPON_CAFE"));

    assertThat(created.code()).isEqualTo("COUPON_CAFE");
    assertThat(created.rewardType()).isEqualTo("COUPON");
    assertThat(created.stock()).isEqualTo(100);

    assertThatThrownBy(
            () -> adminRewardCatalogService.create(operator, createRequest("COUPON_CAFE")))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  @DisplayName("운영자가 아니면 403이다")
  void nonOperatorForbidden() {
    assertThatThrownBy(() -> adminRewardCatalogService.create(normalUser, createRequest("X")))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("잘못된 보상 종류는 400이다")
  void invalidType() {
    AdminRewardCatalogCreateReqDto request =
        new AdminRewardCatalogCreateReqDto(
            "WRONG", "CODE_X", "이름", null, null, null, null, null, true);
    assertThatThrownBy(() -> adminRewardCatalogService.create(operator, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.reward.invalid_type");
  }

  @Test
  @DisplayName("재고·활성 여부를 수정한다")
  void updateCatalog() {
    UUID rewardId =
        UUID.fromString(
            adminRewardCatalogService.create(operator, createRequest("COUPON_A")).rewardId());

    RewardCatalogResDto updated =
        adminRewardCatalogService.update(
            operator, rewardId, new AdminRewardCatalogUpdateReqDto("새 이름", 5, 3, false));

    assertThat(updated.name()).isEqualTo("새 이름");
    assertThat(updated.stock()).isEqualTo(5);
    assertThat(updated.monthlyCap()).isEqualTo(3);
    assertThat(updated.active()).isFalse();
  }

  @Test
  @DisplayName("없는 카탈로그 수정은 404다")
  void updateNotFound() {
    assertThatThrownBy(
            () ->
                adminRewardCatalogService.update(
                    operator,
                    UUID.randomUUID(),
                    new AdminRewardCatalogUpdateReqDto(null, 1, null, null)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("종류·활성 필터로 카탈로그를 조회한다")
  void listWithFilters() {
    adminRewardCatalogService.create(operator, createRequest("COUPON_1"));
    adminRewardCatalogService.create(
        operator,
        new AdminRewardCatalogCreateReqDto(
            "BADGE", "BADGE_1", "배지", null, null, null, null, null, false));

    assertThat(adminRewardCatalogService.list(operator, "COUPON", null)).hasSize(1);
    assertThat(adminRewardCatalogService.list(operator, null, false)).hasSize(1);
    assertThat(adminRewardCatalogService.list(operator, null, null)).hasSize(2);
  }

  @Test
  @DisplayName("지급 이력을 커서 페이징으로 조회한다")
  void grantsCursorPaging() {
    UUID rewardId =
        UUID.fromString(
            adminRewardCatalogService.create(operator, createRequest("COUPON_G")).rewardId());
    RewardCatalog catalog = rewardCatalogRepository.findById(rewardId).orElseThrow();
    UUID userId = savedUser("earner", UserRole.USER).getId();
    for (int i = 0; i < 3; i++) {
      rewardGrantRepository.save(
          RewardGrant.builder()
              .userId(userId)
              .reward(catalog)
              .participationId(UUID.randomUUID())
              .eventId(UUID.randomUUID())
              .grantReason(GrantReason.TOP_LIKE)
              .grantedAt(OffsetDateTime.now().minusMinutes(i))
              .build());
    }

    AdminRewardGrantPageResDto first =
        adminRewardCatalogService.grants(operator, rewardId, null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();

    AdminRewardGrantPageResDto second =
        adminRewardCatalogService.grants(operator, rewardId, first.nextCursor(), 2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.hasNext()).isFalse();
    assertThat(second.items().get(0).grantReason()).isEqualTo("TOP_LIKE");
  }

  @Test
  @DisplayName("없는 카탈로그의 지급 이력·형식 오류 커서는 각각 404·400이다")
  void grantsEdgeCases() {
    assertThatThrownBy(
            () -> adminRewardCatalogService.grants(operator, UUID.randomUUID(), null, 20))
        .isInstanceOf(ResourceNotFoundException.class);

    UUID rewardId =
        UUID.fromString(
            adminRewardCatalogService.create(operator, createRequest("COUPON_E")).rewardId());
    String noPipe =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("nopipe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(() -> adminRewardCatalogService.grants(operator, rewardId, noPipe, 20))
        .isInstanceOf(IllegalArgumentException.class);
    // 기본 size 경로
    assertThat(adminRewardCatalogService.grants(operator, rewardId, null, null).items()).isEmpty();
  }

  private AdminRewardCatalogCreateReqDto createRequest(String code) {
    return new AdminRewardCatalogCreateReqDto(
        "COUPON", code, "카페 쿠폰", null, null, 100, 10, 30, true);
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
}
