package com.butingbe.domain.travelteam.repository;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelMemberRepository extends JpaRepository<TravelMember, UUID> {

  List<TravelMember> findByUser_Id(UUID userId);

  Optional<TravelMember> findByTravel_IdAndUser_Id(UUID travelId, UUID userId);

  @Query(
      """
      select tm
      from TravelMember tm
      join fetch tm.user
      where tm.travel.id = :travelId
      order by tm.role asc, tm.user.nickname asc
      """)
  List<TravelMember> findMembersByTravelId(@Param("travelId") UUID travelId);

  long countByTravel_Id(UUID travelId);

  boolean existsByTravel_IdAndUser_Id(UUID travelId, UUID userId);

  boolean existsByTravel_IdAndUser_IdAndRole(UUID travelId, UUID userId, TravelTeamRole role);

  @Query("select tm.travel from TravelMember tm where tm.user.id = :userId")
  List<Travel> findTravelByUserId(UUID userId);
}
