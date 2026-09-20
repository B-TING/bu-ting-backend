package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository
    extends JpaRepository<TravelRecord, UUID>, JpaSpecificationExecutor<TravelRecord> {

  boolean existsByOriginalTravel_IdAndAuthor_Id(UUID travelId, UUID authorId);

  Optional<TravelRecord> findByOriginalTravel_IdAndAuthor_Id(UUID travelId, UUID authorId);

  List<TravelRecord> findByAuthor_IdOrderByCreatedAtDesc(UUID authorId);

  List<TravelRecord> findByStatusOrderByPublishedAtDescCreatedAtDesc(
      TravelRecordStatus status, Pageable pageable);

  @Query(
      """
      select distinct tr
      from TravelRecordPlace trp
      join trp.travelRecordDay trd
      join trd.travelRecord tr
      where trp.providerPlaceId = :placeId
        and tr.status = :status
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findPublishedRecordsByPlace(
      @Param("placeId") String placeId, @Param("status") TravelRecordStatus status);

  @Query(
      """
      select distinct tr
      from TravelRecordPlace trp
      join trp.travelRecordDay trd
      join trd.travelRecord tr
      where trp.providerPlaceId = :placeId
        and tr.status = :status
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findPublishedRecordsByPlacePage(
      @Param("placeId") String placeId,
      @Param("status") TravelRecordStatus status,
      Pageable pageable);

  @Query(
      """
      select distinct tr
      from TravelRecordPlace trp
      join trp.travelRecordDay trd
      join trd.travelRecord tr
      where trp.providerPlaceId = :placeId
        and tr.status = :status
        and (
          tr.publishedAt < :cursorPublishedAt
          or (tr.publishedAt = :cursorPublishedAt and tr.createdAt < :cursorCreatedAt)
        )
      order by tr.publishedAt desc, tr.createdAt desc
      """)
  List<TravelRecord> findPublishedRecordsByPlacePageAfterCursor(
      @Param("placeId") String placeId,
      @Param("status") TravelRecordStatus status,
      @Param("cursorPublishedAt") LocalDateTime cursorPublishedAt,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      Pageable pageable);

  /** 좋아요 수를 원자적으로 올린다. 읽고 더해 저장하면 동시 요청에서 값이 유실된다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update TravelRecord tr
      set tr.likeCount = tr.likeCount + 1
      where tr.id = :travelRecordId
      """)
  void increaseLikeCount(@Param("travelRecordId") UUID travelRecordId);

  /** 좋아요 수를 원자적으로 내린다. 0 아래로는 내려가지 않는다. */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      update TravelRecord tr
      set tr.likeCount = tr.likeCount - 1
      where tr.id = :travelRecordId and tr.likeCount > 0
      """)
  void decreaseLikeCount(@Param("travelRecordId") UUID travelRecordId);

  /**
   * 조회수를 원자적으로 올린다. 같은 글을 동시에 열면 특히 자주 부딪힌다.
   *
   * <p>호출 뒤 같은 글을 다시 읽어 증가한 값을 응답에 담아야 하므로 영속성 컨텍스트를 비운다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update TravelRecord tr
      set tr.viewCount = tr.viewCount + 1
      where tr.id = :travelRecordId
      """)
  void increaseViewCount(@Param("travelRecordId") UUID travelRecordId);

  /** 벌크 갱신 직후의 좋아요 수. 영속성 컨텍스트의 엔티티는 갱신 전 값을 들고 있다. */
  @Query("select tr.likeCount from TravelRecord tr where tr.id = :travelRecordId")
  Optional<Long> findLikeCount(@Param("travelRecordId") UUID travelRecordId);
}
