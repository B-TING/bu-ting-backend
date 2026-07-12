package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, UUID> {

  Optional<PlaceReview> findByTravelRecordPlace_Id(UUID travelRecordPlaceId);

  @Query(
      """
      select pr
      from PlaceReview pr
      join pr.travelRecordPlace trp
      join trp.travelRecordDay trd
      join trd.travelRecord tr
      where trp.provider = :provider
        and trp.providerPlaceId = :providerPlaceId
        and tr.status = :status
      """)
  List<PlaceReview> findByPlaceAndRecordStatus(
      @Param("provider") PlaceProvider provider,
      @Param("providerPlaceId") String providerPlaceId,
      @Param("status") TravelRecordStatus status);
}
