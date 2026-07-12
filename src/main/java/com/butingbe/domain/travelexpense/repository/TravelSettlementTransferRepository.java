package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelSettlementTransfer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelSettlementTransferRepository
    extends JpaRepository<TravelSettlementTransfer, UUID> {

  @EntityGraph(attributePaths = {"fromUser", "toUser"})
  List<TravelSettlementTransfer>
      findBySettlement_IdOrderByCurrencyAscAmountDescFromUser_NicknameAscFromUser_IdAsc(
          UUID settlementId);
}
