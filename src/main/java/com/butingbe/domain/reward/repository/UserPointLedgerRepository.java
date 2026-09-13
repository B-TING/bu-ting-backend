package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.UserPointLedger;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointLedgerRepository
    extends JpaRepository<UserPointLedger, UUID>, JpaSpecificationExecutor<UserPointLedger> {

  @Query("SELECT COALESCE(SUM(l.amount), 0) FROM UserPointLedger l WHERE l.userId = :userId")
  long sumAmountByUserId(@Param("userId") UUID userId);

  @Query("SELECT COALESCE(SUM(l.amount), 0) FROM UserPointLedger l WHERE l.grantId = :grantId")
  long sumAmountByGrantId(@Param("grantId") UUID grantId);
}
