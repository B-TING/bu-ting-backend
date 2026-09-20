package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelPlanQualityValidator {
  private final TravelPlanRoutePlanner routePlanner;

  public List<String> feedback(
      TravelPlanAiResponse response,
      Map<LocalDate, List<PlaceKey>> routes,
      Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    List<String> feedback = new ArrayList<>();
    Set<String> memos = new HashSet<>();
    for (var day : response.days()) {
      var actual =
          day.places().stream()
              .map(place -> PlaceKey.of(place.provider(), place.providerPlaceId()))
              .toList();
      var expected = routes.get(day.date());
      if (!new HashSet<>(actual).equals(new HashSet<>(expected))) {
        feedback.add(day.date() + ": 서버가 지정한 날짜별 장소 묶음을 유지하세요.");
      } else if (actual.stream().allMatch(key -> routePlanner.located(catalog.get(key)))
          && routePlanner.length(actual, catalog)
              > routePlanner.length(expected, catalog) * 1.3 + 2) {
        feedback.add(day.date() + ": 불필요한 왕복으로 직선 이동 거리가 큽니다. 서버의 추천 순서를 사용하세요.");
      }
      for (var place : day.places()) {
        String memo = place.memo() == null ? "" : place.memo().strip();
        String expectedName =
            catalog.get(PlaceKey.of(place.provider(), place.providerPlaceId())).placeName();
        // 모델이 이 ID를 어느 장소로 이해했는지 확인한다. 장소명은 서버가 원본에서 채우므로,
        // 이 대조가 없으면 ID와 설명이 어긋나도 알아챌 방법이 없다.
        if (!normalize(place.placeName()).equals(normalize(expectedName))) {
          feedback.add(
              day.date()
                  + " order="
                  + place.order()
                  + ": placeName에 이 providerPlaceId의 원본 장소명을 그대로 복사하세요. 지금 값은 \""
                  + (place.placeName() == null ? "" : place.placeName().strip())
                  + "\"이고 기대값은 \""
                  + expectedName
                  + "\"입니다.");
        }
        // memo에는 장소명을 적지 않으므로 본문 전체가 설명이다.
        String body = normalize(memo);
        if (body.length() < 15
            || memo.contains("<")
            || memo.contains("구체적으로 작성")
            || body.equals("추천이유")
            || body.equals("방문하기좋습니다")
            || !memos.add(body)) {
          feedback.add(
              day.date()
                  + " order="
                  + place.order()
                  + ": 예시 문구나 반복 설명 대신 해당 장소의 활동·여행 목적·배치 근거를 구체적으로 1~2문장 작성하세요.");
        }
      }
    }
    return List.copyOf(feedback);
  }

  /** 구두점·공백 차이로 비교가 갈리지 않게 한다. 괄호가 붙은 카탈로그 이름도 같은 기준으로 다룬다. */
  private String normalize(String value) {
    return value == null ? "" : value.replaceAll("[\\p{P}\\p{Z}\\s]", "");
  }
}
