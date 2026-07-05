package com.butingbe.domain.travel.dto.response;

import com.butingbe.domain.travel.entity.Plan;
import java.time.LocalDate;
import java.util.UUID;

public record PlanResDto(UUID planId, UUID travelId, Integer dayNumber, LocalDate visitDate) {

  public static PlanResDto from(Plan plan) {
    return new PlanResDto(
        plan.getId(), plan.getTravel().getId(), plan.getDayNumber(), plan.getVisitDate());
  }
}
