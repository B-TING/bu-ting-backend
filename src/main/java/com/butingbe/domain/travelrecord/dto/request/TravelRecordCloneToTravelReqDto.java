package com.butingbe.domain.travelrecord.dto.request;

import com.butingbe.domain.travel.entity.CompanionType;
import com.butingbe.domain.travel.entity.TravelPace;
import com.butingbe.domain.travel.entity.TravelStyle;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TravelRecordCloneToTravelReqDto(
    @Size(max = 15, message = "Travel title must be 15 characters or less.") String title,
    @NotNull(message = "Travel start date is required.") LocalDate startDate,
    Boolean hasHeavyBaggage,
    Boolean hasPets,
    TravelStyle travelStyle,
    Boolean preferFlatTerrain,
    TravelPace pace,
    Integer companionCount,
    String preferredFoods,
    @JsonAlias("companionTypes") CompanionType companionType,
    String accommodationArea) {

  public CompanionType companionTypes() {
    return companionType;
  }
}
