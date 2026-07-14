package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordLikeRepository extends JpaRepository<TravelRecordLike, UUID> {

  boolean existsByUser_IdAndTravelRecord_Id(UUID userId, UUID travelRecordId);

  Optional<TravelRecordLike> findByUser_IdAndTravelRecord_Id(UUID userId, UUID travelRecordId);

  @Query(
      """
      select travelRecordLike.travelRecord.id
      from TravelRecordLike travelRecordLike
      where travelRecordLike.user.id = :userId
        and travelRecordLike.travelRecord.id in :travelRecordIds
      """)
  List<UUID> findLikedTravelRecordIds(
      @Param("userId") UUID userId, @Param("travelRecordIds") Collection<UUID> travelRecordIds);
}
