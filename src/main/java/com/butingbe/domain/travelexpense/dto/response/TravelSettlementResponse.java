package com.butingbe.domain.travelexpense.dto.response;

import com.butingbe.domain.travelexpense.entity.TravelSettlement;
import com.butingbe.domain.travelexpense.entity.TravelSettlementTransfer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelSettlementResponse(
    UUID travelId,
    boolean confirmed,
    UUID confirmedById,
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
      UUID senderId,
      String senderNickname,
      UUID receiverId,
      String receiverNickname,
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

    @JsonIgnore
    public UUID fromUserId() {
      return senderId;
    }

    @JsonIgnore
    public String fromNickname() {
      return senderNickname;
    }

    @JsonIgnore
    public UUID toUserId() {
      return receiverId;
    }

    @JsonIgnore
    public String toNickname() {
      return receiverNickname;
    }
  }

  @JsonIgnore
  public UUID confirmedByUserId() {
    return confirmedById;
  }
}
