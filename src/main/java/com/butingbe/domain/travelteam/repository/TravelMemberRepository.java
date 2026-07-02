package com.butingbe.domain.travelteam.repository;

import com.butingbe.domain.temp.entity.TravelTemp;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TravelMemberRepository extends JpaRepository<TravelMember, UUID> {

    List<TravelMember> findByUserId(UUID userId);

    @Query("select tm.travel from TravelMember tm where tm.user.id = :userId")
    List<TravelTemp> findTravelByUserId(UUID userId); // TravelTemp는 Travel 대신 임시로 설정해놓은거
}
