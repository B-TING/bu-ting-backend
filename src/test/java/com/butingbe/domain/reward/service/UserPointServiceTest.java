package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.reward.repository.UserPointBalanceRepository;
import com.butingbe.domain.reward.repository.UserPointLedgerRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserPointServiceTest extends AbstractContainerTest {

  @Autowired private UserPointService userPointService;
  @Autowired private UserPointLedgerRepository ledgerRepository;
  @Autowired private UserPointBalanceRepository balanceRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("포인트 증감마다 원장을 남기고 잔액은 원장 합과 일치한다")
  void balanceMatchesLedgerSum() {
    UUID userId = savedUser();

    int afterFirst = userPointService.record(userId, 50, "BASE", null);
    int afterSecond = userPointService.record(userId, 30, "BASE", null);

    assertThat(afterFirst).isEqualTo(50);
    assertThat(afterSecond).isEqualTo(80);
    assertThat(userPointService.getBalance(userId)).isEqualTo(80);
    assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(80);
    assertThat(balanceRepository.findById(userId).orElseThrow().getBalance())
        .isEqualTo((int) ledgerRepository.sumAmountByUserId(userId));
  }

  @Test
  @DisplayName("잔액이 없는 유저의 조회는 0이다")
  void balanceIsZeroWhenNoLedger() {
    assertThat(userPointService.getBalance(UUID.randomUUID())).isZero();
  }

  @Test
  @DisplayName("회수로 잔액이 음수가 되면 0에서 멈춘다")
  void balanceStopsAtZeroOnRevoke() {
    UUID userId = savedUser();
    userPointService.record(userId, 50, "BASE", null);

    int afterRevoke = userPointService.record(userId, -80, "REVOKE", null);

    assertThat(afterRevoke).isZero();
    assertThat(userPointService.getBalance(userId)).isZero();
    // 원장은 실제 증감을 그대로 남긴다(50 - 80 = -30).
    assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(-30);
  }

  private UUID savedUser() {
    User user =
        userRepository.save(
            User.builder()
                .email("point-" + UUID.randomUUID() + "@example.com")
                .provider("google")
                .providerId("google-point-" + UUID.randomUUID())
                .name(new Name("Kim", "Tester"))
                .nickname("pointer")
                .role(UserRole.USER)
                .build());
    return user.getId();
  }
}
