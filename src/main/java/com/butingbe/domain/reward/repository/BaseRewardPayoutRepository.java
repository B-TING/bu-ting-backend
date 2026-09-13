package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaseRewardPayoutRepository extends JpaRepository<BaseRewardPayout, UUID> {

  Optional<BaseRewardPayout> findByParticipationId(UUID participationId);

  @Query(
      value =
          "select r from BaseRewardPayout r, ZoneEventParticipation p "
              + "where r.participationId = p.id "
              + "and (:eventId is null or p.event.id = :eventId) "
              + "and (:roundId is null or p.event.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (cast(:scheduledFrom as OffsetDateTime) is null or r.scheduledAt >= :scheduledFrom) "
              + "and (cast(:scheduledTo as OffsetDateTime) is null or r.scheduledAt <= :scheduledTo) "
              + "order by r.createdAt desc",
      countQuery =
          "select count(r) from BaseRewardPayout r, ZoneEventParticipation p "
              + "where r.participationId = p.id "
              + "and (:eventId is null or p.event.id = :eventId) "
              + "and (:roundId is null or p.event.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (cast(:scheduledFrom as OffsetDateTime) is null or r.scheduledAt >= :scheduledFrom) "
              + "and (cast(:scheduledTo as OffsetDateTime) is null or r.scheduledAt <= :scheduledTo)")
  Page<BaseRewardPayout> searchForAdmin(
      @Param("eventId") UUID eventId,
      @Param("roundId") UUID roundId,
      @Param("status") BaseRewardPayoutStatus status,
      @Param("holdStatus") PayoutHoldStatus holdStatus,
      @Param("scheduledFrom") OffsetDateTime scheduledFrom,
      @Param("scheduledTo") OffsetDateTime scheduledTo,
      Pageable pageable);

  @Query(
      "SELECT COUNT(b) FROM BaseRewardPayout b, ZoneEventParticipation p "
          + "WHERE b.participationId = p.id AND p.event.id = :eventId AND b.status = :status")
  long countByEventIdAndStatus(
      @Param("eventId") UUID eventId, @Param("status") BaseRewardPayoutStatus status);
}
