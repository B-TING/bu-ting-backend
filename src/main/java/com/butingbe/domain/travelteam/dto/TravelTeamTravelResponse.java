package com.butingbe.domain.travelteam.dto;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelTeamTravelResponse(
    UUID travelId,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    TravelStatus status,
    TravelTeamRole role,
    LocalDateTime createdAt) {

  public static TravelTeamTravelResponse from(TravelMember member) {
    Travel travel = member.getTravel();
    return new TravelTeamTravelResponse(
        travel.getId(),
        travel.getTitle(),
        travel.getStartDate(),
        travel.getEndDate(),
        travel.getStatus(),
        member.getRole(),
        travel.getCreatedAt());
  }
}
