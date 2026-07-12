package com.butingbe.domain.travelexpense.dto.response;

import com.butingbe.domain.travelexpense.entity.TravelSettlement;
import com.butingbe.domain.travelexpense.entity.TravelSettlementTransfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelSettlementResponse(
    UUID travelId,
    boolean confirmed,
    UUID confirmedByUserId,
    LocalDateTime confirmedAt,
    List<Transfer> transfers) {

  public static TravelSettlementResponse preview(UUID travelId, List<Transfer> transfers) {
    return new TravelSettlementResponse(travelId, false, null, null, transfers);
  }

  public static TravelSettlementResponse confirmed(
      TravelSettlement settlement, List<TravelSettlementTransfer> transfers) {
    return new TravelSettlementResponse(
        settlement.getTravel().getId(),
        true,
        settlement.getConfirmedBy().getId(),
        settlement.getConfirmedAt(),
        transfers.stream().map(Transfer::from).toList());
  }

  public record Transfer(
      String currency,
      UUID fromUserId,
      String fromNickname,
      UUID toUserId,
      String toNickname,
      long amount) {

    private static Transfer from(TravelSettlementTransfer transfer) {
      return new Transfer(
          transfer.getCurrency(),
          transfer.getFromUser().getId(),
          transfer.getFromUser().getNickname(),
          transfer.getToUser().getId(),
          transfer.getToUser().getNickname(),
          transfer.getAmount());
    }
  }
}
