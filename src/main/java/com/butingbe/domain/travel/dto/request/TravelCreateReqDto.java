package com.butingbe.domain.travel.dto.request;

import com.butingbe.domain.travel.entity.CompanionType;
import com.butingbe.domain.travel.entity.TravelPace;
import com.butingbe.domain.travel.entity.TravelStyle;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TravelCreateReqDto(
    @Size(max = 15, message = "여행 제목은 15자 이하로 입력해주세요.") String title,
    @NotNull(message = "여행 시작 날짜는 필수입니다.") LocalDate startDate,
    @NotNull(message = "여행 종료 날짜는 필수입니다.") LocalDate endDate,
    @NotBlank(message = "여행 지역은 필수입니다.") String destination,
    Boolean hasHeavyBaggage,
    Boolean hasPets,
    TravelStyle travelStyle,
    Boolean preferFlatTerrain,
    TravelPace pace,
    Integer companionCount,
    String preferredFoods,
    @JsonAlias("companionTypes") CompanionType companionType,
    String accommodationArea) {

  public TravelCreateReqDto(
      String title,
      LocalDate startDate,
      LocalDate endDate,
      Boolean hasHeavyBaggage,
      Boolean hasPets,
      TravelStyle travelStyle,
      Boolean preferFlatTerrain,
      TravelPace pace,
      Integer companionCount,
      String preferredFoods,
      CompanionType companionType,
      String accommodationArea) {
    this(
        title,
        startDate,
        endDate,
        null,
        hasHeavyBaggage,
        hasPets,
        travelStyle,
        preferFlatTerrain,
        pace,
        companionCount,
        preferredFoods,
        companionType,
        accommodationArea);
  }

  public CompanionType companionTypes() {
    return companionType;
  }
}
