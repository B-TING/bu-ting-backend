package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.time.LocalDate;
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
          :hasKeyword = false
          or lower(tr.title) like :keywordPattern
          or lower(coalesce(tr.content, '')) like :keywordPattern
          or exists (
            select 1
            from TravelRecordPlace trpKeyword
            join trpKeyword.travelRecordDay trdKeyword
            where trdKeyword.travelRecord = tr
              and lower(trpKeyword.placeName) like :keywordPattern
          )
        )
        and (
          :hasPlace = false
          or exists (
            select 1
            from TravelRecordPlace trpPlace
            join trpPlace.travelRecordDay trdPlace
            where trdPlace.travelRecord = tr
              and trpPlace.provider = :provider
              and trpPlace.providerPlaceId = :providerPlaceId
          )
        )
        and (:hasTravelStartDate = false or tr.travelEndDate >= :travelStartDate)
        and (:hasTravelEndDate = false or tr.travelStartDate <= :travelEndDate)
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPage(
      @Param("status") TravelRecordStatus status,
      @Param("hasKeyword") boolean hasKeyword,
      @Param("keywordPattern") String keywordPattern,
      @Param("hasPlace") boolean hasPlace,
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("hasTravelStartDate") boolean hasTravelStartDate,
      @Param("travelStartDate") LocalDate travelStartDate,
      @Param("hasTravelEndDate") boolean hasTravelEndDate,
      @Param("travelEndDate") LocalDate travelEndDate,
      Pageable pageable);

  @Query(
      """
      select tr
      from TravelRecord tr
      where tr.status = :status
        and (
          tr.publishedAt < :cursorPublishedAt
          or (tr.publishedAt = :cursorPublishedAt and tr.createdAt < :cursorCreatedAt)
        )
        and (
          :hasKeyword = false
          or lower(tr.title) like :keywordPattern
          or lower(coalesce(tr.content, '')) like :keywordPattern
          or exists (
            select 1
            from TravelRecordPlace trpKeyword
            join trpKeyword.travelRecordDay trdKeyword
            where trdKeyword.travelRecord = tr
              and lower(trpKeyword.placeName) like :keywordPattern
          )
        )
        and (
          :hasPlace = false
          or exists (
            select 1
            from TravelRecordPlace trpPlace
            join trpPlace.travelRecordDay trdPlace
            where trdPlace.travelRecord = tr
              and trpPlace.provider = :provider
              and trpPlace.providerPlaceId = :providerPlaceId
          )
        )
        and (:hasTravelStartDate = false or tr.travelEndDate >= :travelStartDate)
        and (:hasTravelEndDate = false or tr.travelStartDate <= :travelEndDate)
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPageAfterCursor(
      @Param("status") TravelRecordStatus status,
      @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("hasKeyword") boolean hasKeyword,
      @Param("keywordPattern") String keywordPattern,
      @Param("hasPlace") boolean hasPlace,
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("hasTravelStartDate") boolean hasTravelStartDate,
      @Param("travelStartDate") LocalDate travelStartDate,
      @Param("hasTravelEndDate") boolean hasTravelEndDate,
      @Param("travelEndDate") LocalDate travelEndDate,
      Pageable pageable);

  @Query(
      """
      select tr
      from TravelRecord tr
      where tr.status = :status
        and (
          :hasKeyword = false
          or lower(tr.title) like :keywordPattern
          or lower(coalesce(tr.content, '')) like :keywordPattern
          or exists (
            select 1
            from TravelRecordPlace trpKeyword
            join trpKeyword.travelRecordDay trdKeyword
            where trdKeyword.travelRecord = tr
              and lower(trpKeyword.placeName) like :keywordPattern
          )
        )
        and (
          :hasPlace = false
          or exists (
            select 1
            from TravelRecordPlace trpPlace
            join trpPlace.travelRecordDay trdPlace
            where trdPlace.travelRecord = tr
              and trpPlace.provider = :provider
              and trpPlace.providerPlaceId = :providerPlaceId
          )
        )
        and (:hasTravelStartDate = false or tr.travelEndDate >= :travelStartDate)
        and (:hasTravelEndDate = false or tr.travelStartDate <= :travelEndDate)
      order by tr.likeCount desc, tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPageOrderByLikeCount(
      @Param("status") TravelRecordStatus status,
      @Param("hasKeyword") boolean hasKeyword,
      @Param("keywordPattern") String keywordPattern,
      @Param("hasPlace") boolean hasPlace,
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("hasTravelStartDate") boolean hasTravelStartDate,
      @Param("travelStartDate") LocalDate travelStartDate,
      @Param("hasTravelEndDate") boolean hasTravelEndDate,
      @Param("travelEndDate") LocalDate travelEndDate,
      Pageable pageable);

  @Query(
      """
      select tr
      from TravelRecord tr
      where tr.status = :status
        and (
          tr.likeCount < :cursorSortCount
          or (tr.likeCount = :cursorSortCount and tr.publishedAt < :cursorPublishedAt)
          or (
            tr.likeCount = :cursorSortCount
            and tr.publishedAt = :cursorPublishedAt
            and tr.createdAt < :cursorCreatedAt
          )
        )
        and (
          :hasKeyword = false
          or lower(tr.title) like :keywordPattern
          or lower(coalesce(tr.content, '')) like :keywordPattern
          or exists (
            select 1
            from TravelRecordPlace trpKeyword
            join trpKeyword.travelRecordDay trdKeyword
            where trdKeyword.travelRecord = tr
              and lower(trpKeyword.placeName) like :keywordPattern
          )
        )
        and (
          :hasPlace = false
          or exists (
            select 1
            from TravelRecordPlace trpPlace
            join trpPlace.travelRecordDay trdPlace
            where trdPlace.travelRecord = tr
              and trpPlace.provider = :provider
              and trpPlace.providerPlaceId = :providerPlaceId
          )
        )
        and (:hasTravelStartDate = false or tr.travelEndDate >= :travelStartDate)
        and (:hasTravelEndDate = false or tr.travelStartDate <= :travelEndDate)
      order by tr.likeCount desc, tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPageAfterCursorOrderByLikeCount(
      @Param("status") TravelRecordStatus status,
      @Param("cursorSortCount") long cursorSortCount,
      @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("hasKeyword") boolean hasKeyword,
      @Param("keywordPattern") String keywordPattern,
      @Param("hasPlace") boolean hasPlace,
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("hasTravelStartDate") boolean hasTravelStartDate,
      @Param("travelStartDate") LocalDate travelStartDate,
      @Param("hasTravelEndDate") boolean hasTravelEndDate,
      @Param("travelEndDate") LocalDate travelEndDate,
      Pageable pageable);

  @Query(
      """
      select tr
      from TravelRecord tr
      where tr.status = :status
        and (
          :hasKeyword = false
          or lower(tr.title) like :keywordPattern
          or lower(coalesce(tr.content, '')) like :keywordPattern
          or exists (
            select 1
            from TravelRecordPlace trpKeyword
            join trpKeyword.travelRecordDay trdKeyword
            where trdKeyword.travelRecord = tr
              and lower(trpKeyword.placeName) like :keywordPattern
          )
        )
        and (
          :hasPlace = false
          or exists (
            select 1
            from TravelRecordPlace trpPlace
            join trpPlace.travelRecordDay trdPlace
            where trdPlace.travelRecord = tr
              and trpPlace.provider = :provider
              and trpPlace.providerPlaceId = :providerPlaceId
          )
        )
        and (:hasTravelStartDate = false or tr.travelEndDate >= :travelStartDate)
        and (:hasTravelEndDate = false or tr.travelStartDate <= :travelEndDate)
      order by tr.viewCount desc, tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPageOrderByViewCount(
      @Param("status") TravelRecordStatus status,
      @Param("hasKeyword") boolean hasKeyword,
      @Param("keywordPattern") String keywordPattern,
      @Param("hasPlace") boolean hasPlace,
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("hasTravelStartDate") boolean hasTravelStartDate,
      @Param("travelStartDate") LocalDate travelStartDate,
      @Param("hasTravelEndDate") boolean hasTravelEndDate,
      @Param("travelEndDate") LocalDate travelEndDate,
      Pageable pageable);

  @Query(
      """
      select tr
      from TravelRecord tr
      where tr.status = :status
        and (
          tr.viewCount < :cursorSortCount
          or (tr.viewCount = :cursorSortCount and tr.publishedAt < :cursorPublishedAt)
          or (
            tr.viewCount = :cursorSortCount
            and tr.publishedAt = :cursorPublishedAt
            and tr.createdAt < :cursorCreatedAt
          )
        )
        and (
          :hasKeyword = false
          or lower(tr.title) like :keywordPattern
          or lower(coalesce(tr.content, '')) like :keywordPattern
          or exists (
            select 1
            from TravelRecordPlace trpKeyword
            join trpKeyword.travelRecordDay trdKeyword
            where trdKeyword.travelRecord = tr
              and lower(trpKeyword.placeName) like :keywordPattern
          )
        )
        and (
          :hasPlace = false
          or exists (
            select 1
            from TravelRecordPlace trpPlace
            join trpPlace.travelRecordDay trdPlace
            where trdPlace.travelRecord = tr
              and trpPlace.provider = :provider
              and trpPlace.providerPlaceId = :providerPlaceId
          )
        )
        and (:hasTravelStartDate = false or tr.travelEndDate >= :travelStartDate)
        and (:hasTravelEndDate = false or tr.travelStartDate <= :travelEndDate)
      order by tr.viewCount desc, tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findFeedPageAfterCursorOrderByViewCount(
      @Param("status") TravelRecordStatus status,
      @Param("cursorSortCount") long cursorSortCount,
      @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("hasKeyword") boolean hasKeyword,
      @Param("keywordPattern") String keywordPattern,
      @Param("hasPlace") boolean hasPlace,
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("hasTravelStartDate") boolean hasTravelStartDate,
      @Param("travelStartDate") LocalDate travelStartDate,
      @Param("hasTravelEndDate") boolean hasTravelEndDate,
      @Param("travelEndDate") LocalDate travelEndDate,
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
  List<TravelRecord> findPublishedRecordsByPlacePage(
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("status") TravelRecordStatus status,
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
        and (
          tr.publishedAt < :cursorPublishedAt
          or (tr.publishedAt = :cursorPublishedAt and tr.createdAt < :cursorCreatedAt)
        )
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findPublishedRecordsByPlacePageAfterCursor(
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("status") TravelRecordStatus status,
      @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      Pageable pageable);
}
