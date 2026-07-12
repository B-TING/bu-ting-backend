package com.butingbe.domain.travel.dto.response;

import com.butingbe.domain.travel.entity.CompanionType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelPace;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.entity.TravelStyle;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelResDto(
    UUID id,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    TravelStatus status,
    LocalDateTime createdAt,
    Boolean hasHeavyBaggage,
    Boolean hasPets,
    TravelStyle travelStyle,
    Boolean preferFlatTerrain,
    TravelPace pace,
    Integer companionCount,
    String preferredFoods,
    CompanionType companionTypes,
    String accommodationArea) {

  public static TravelResDto from(Travel travel) {
    return new TravelResDto(
        travel.getId(),
        travel.getTitle(),
        travel.getStartDate(),
        travel.getEndDate(),
        travel.getStatus(),
        travel.getCreatedAt(),
        travel.getHasHeavyBaggage(),
        travel.getHasPets(),
        travel.getTravelStyle(),
        travel.getPreferFlatTerrain(),
        travel.getPace(),
        travel.getCompanionCount(),
        travel.getPreferredFoods(),
        travel.getCompanionTypes(),
        travel.getAccommodationArea());
  }
}
