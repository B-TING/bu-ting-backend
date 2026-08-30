package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.Travel;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelPlanGenerator {
  private final TravelPlanPromptBuilder promptBuilder;
  private final TravelPlanAiClient aiClient;
  private final TravelPlanAiResponseValidator validator;
  private final TravelPlanRoutePlanner routePlanner;
  private final TravelPlanQualityValidator qualityValidator;

  public TravelPlanAiResponse generate(
      Travel travel,
      AiTravelPlanGenerateReqDto request,
      Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    var routes = routePlanner.plan(travel, catalog);
    String prompt =
        promptBuilder.build(travel, request)
            + "\n서버가 좌표로 정한 날짜별 장소 묶음과 추천 순서: "
            + routes
            + "\n날짜별 묶음은 반드시 유지하세요. 날짜 안의 순서는 불필요한 왕복 없이 조정할 수 있습니다."
            + "\n좌표 없는 장소는 수량 기준으로 배치했으며 정확한 거리나 이동 시간을 추측하지 마세요.";
    for (int attempt = 0; attempt < 2; attempt++) {
      var response = aiClient.generate(prompt);
      validator.validate(travel, response, catalog);
      var feedback = qualityValidator.feedback(response, routes, catalog);
      if (feedback.isEmpty()) return response;
      if (attempt == 1) {
        throw new TravelPlanValidationException(
            TravelPlanValidationException.Reason.LOW_QUALITY_PLAN, true, Set.of());
      }
      prompt +=
          "\n직전 결과의 품질 검증 실패 항목:\n"
              + String.join("\n", feedback)
              + "\n위 문제를 수정하여 전체 일정을 다시 반환하세요. 모든 선택 장소와 날짜 범위는 유지하세요.";
    }
    throw new IllegalStateException("Unreachable generation state");
  }
}
