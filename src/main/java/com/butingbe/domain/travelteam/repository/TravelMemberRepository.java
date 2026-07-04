package com.butingbe.domain.travelteam.repository;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TravelMemberRepository extends JpaRepository<TravelMember, UUID> {

  List<TravelMember> findByUser_Id(UUID userId);

  boolean existsByTravel_IdAndUser_Id(UUID travelId, UUID userId);

  boolean existsByTravel_IdAndUser_IdAndRole(UUID travelId, UUID userId, TravelTeamRole role);

  @Query("select tm.travel from TravelMember tm where tm.user.id = :userId")
  List<Travel> findTravelByUserId(UUID userId);
}
