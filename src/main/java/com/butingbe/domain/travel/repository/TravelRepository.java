package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRepository extends JpaRepository<Travel, UUID> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Travel t
      set t.status = :completedStatus
      where t.status in :targetStatuses
        and t.endDate < :today
      """)
  int completeEndedTravels(
      @Param("today") LocalDate today,
      @Param("targetStatuses") Iterable<TravelStatus> targetStatuses,
      @Param("completedStatus") TravelStatus completedStatus);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Travel t
      set t.status = :inProgressStatus
      where t.status = :plannedStatus
        and t.startDate <= :today
        and t.endDate >= :today
      """)
  int startPlannedTravels(
      @Param("today") LocalDate today,
      @Param("plannedStatus") TravelStatus plannedStatus,
      @Param("inProgressStatus") TravelStatus inProgressStatus);
}
