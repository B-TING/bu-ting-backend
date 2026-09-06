package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.UserPointLedger;
import java.time.OffsetDateTime;

/** 포인트 원장 한 항목. */
public record PointLedgerItemResDto(
    String ledgerId, int amount, String reason, String grantId, OffsetDateTime createdAt) {

  public static PointLedgerItemResDto from(UserPointLedger ledger) {
    return new PointLedgerItemResDto(
        ledger.getId().toString(),
        ledger.getAmount(),
        ledger.getReason(),
        ledger.getGrantId() == null ? null : ledger.getGrantId().toString(),
        ledger.getCreatedAt());
  }
}
