package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RewardPayoutRepository extends JpaRepository<RewardPayout, UUID> {

  List<RewardPayout> findByEventId(UUID eventId);

  boolean existsByParticipationId(UUID participationId);

  Optional<RewardPayout> findByParticipationId(UUID participationId);

  @Query(
      value =
          "select r from RewardPayout r, ZoneEvent e "
              + "where r.eventId = e.id "
              + "and (:eventId is null or r.eventId = :eventId) "
              + "and (:roundId is null or e.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (cast(:scheduledFrom as OffsetDateTime) is null or r.scheduledAt >= :scheduledFrom) "
              + "and (cast(:scheduledTo as OffsetDateTime) is null or r.scheduledAt <= :scheduledTo) "
              + "order by r.createdAt desc",
      countQuery =
          "select count(r) from RewardPayout r, ZoneEvent e "
              + "where r.eventId = e.id "
              + "and (:eventId is null or r.eventId = :eventId) "
              + "and (:roundId is null or e.roundId = :roundId) "
              + "and (:status is null or r.status = :status) "
              + "and (:holdStatus is null or r.holdStatus = :holdStatus) "
              + "and (cast(:scheduledFrom as OffsetDateTime) is null or r.scheduledAt >= :scheduledFrom) "
              + "and (cast(:scheduledTo as OffsetDateTime) is null or r.scheduledAt <= :scheduledTo)")
  Page<RewardPayout> searchForAdmin(
      @Param("eventId") UUID eventId,
      @Param("roundId") UUID roundId,
      @Param("status") RewardPayoutStatus status,
      @Param("holdStatus") PayoutHoldStatus holdStatus,
      @Param("scheduledFrom") OffsetDateTime scheduledFrom,
      @Param("scheduledTo") OffsetDateTime scheduledTo,
      Pageable pageable);

  long countByEventIdAndStatus(UUID eventId, RewardPayoutStatus status);
}
