package com.butingbe.domain.reward.service;

import com.butingbe.domain.reward.entity.UserPointBalance;
import com.butingbe.domain.reward.entity.UserPointLedger;
import com.butingbe.domain.reward.repository.UserPointBalanceRepository;
import com.butingbe.domain.reward.repository.UserPointLedgerRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 포인트를 원장과 잔액 캐시로 관리한다.
 *
 * <p>모든 증감은 원장에 한 줄을 남기고 같은 트랜잭션에서 잔액 캐시를 갱신하므로, 잔액은 원장 합과 항상 일치한다(FR-RWD-03). 잔액이 음수가 되지 않도록
 * {@link UserPointBalance#add(int)}가 0에서 멈춘다(BR-07).
 */
@Service
@RequiredArgsConstructor
public class UserPointService {

  private final UserPointLedgerRepository ledgerRepository;
  private final UserPointBalanceRepository balanceRepository;

  /** 포인트를 증감하고 원장·잔액을 함께 기록한다. */
  @Transactional
  public int record(UUID userId, int amount, String reason, UUID grantId) {
    ledgerRepository.save(
        UserPointLedger.builder()
            .userId(userId)
            .amount(amount)
            .reason(reason)
            .grantId(grantId)
            .build());

    UserPointBalance balance =
        balanceRepository
            .findById(userId)
            .orElseGet(() -> UserPointBalance.builder().userId(userId).balance(0).build());
    balance.add(amount);
    return balanceRepository.save(balance).getBalance();
  }

  /** 현재 잔액. 원장이 없으면 0. */
  @Transactional(readOnly = true)
  public int getBalance(UUID userId) {
    return balanceRepository.findById(userId).map(UserPointBalance::getBalance).orElse(0);
  }
}
