package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, UUID> {

  boolean existsByOriginalTravel_IdAndAuthor_Id(UUID travelId, UUID authorId);

  Optional<TravelRecord> findByOriginalTravel_IdAndAuthor_Id(UUID travelId, UUID authorId);

  List<TravelRecord> findByAuthor_IdOrderByCreatedAtDesc(UUID authorId);

  List<TravelRecord> findByStatusOrderByPublishedAtDescCreatedAtDesc(
      TravelRecordStatus status, Pageable pageable);

  @Query(
      """
      select tr
      from TravelRecord tr
      where tr.status = :status
        and (
          tr.publishedAt < :cursorPublishedAt
          or (tr.publishedAt = :cursorPublishedAt and tr.createdAt < :cursorCreatedAt)
        )
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPageAfterCursor(
      @Param("status") TravelRecordStatus status,
      @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      Pageable pageable);

  @Query(
      """
      select distinct tr
      from TravelRecordPlace trp
      join trp.travelRecordDay trd
      join trd.travelRecord tr
      where trp.provider = :provider
        and trp.providerPlaceId = :providerPlaceId
        and tr.status = :status
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findPublishedRecordsByPlace(
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("status") TravelRecordStatus status);
}
