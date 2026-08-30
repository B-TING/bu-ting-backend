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
        String memo = place.memo().strip();
        String normalized = memo.replaceAll("[\\p{P}\\p{Z}\\s]", "");
        String name =
            catalog.get(PlaceKey.of(place.provider(), place.providerPlaceId())).placeName();
        String normalizedName = name.replaceAll("[\\p{P}\\p{Z}\\s]", "");
        if (!normalized.startsWith(normalizedName)) {
          feedback.add(
              day.date()
                  + " order="
                  + place.order()
                  + ": memo를 이 ID에 해당하는 원본 장소명으로 시작하세요. 다른 장소의 설명을 붙이지 마세요.");
        }
        String body = normalized.replace(normalizedName, "");
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
}
