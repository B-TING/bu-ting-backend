package com.butingbe.domain.travel.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

public record TravelPlanAiResponse(List<Day> days) {
  public record Day(LocalDate date, List<Place> places) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  /**
   * 모델이 돌려주는 장소 한 건.
   *
   * <p>{@code placeName}은 저장하지 않는다. 모델이 이 ID를 어느 장소로 이해했는지 확인하는 용도로만 쓰고 버린다. 장소명·주소·좌표는 요청 원본에서 서버가
   * 채우므로, 이 신호가 없으면 모델이 ID와 설명을 뒤섞어도 알아챌 방법이 없다.
   */
  public record Place(
      int order, String provider, String providerPlaceId, String placeName, String memo) {}
}
