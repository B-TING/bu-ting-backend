package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

public final class TravelPlanFixtures {
  public static final LocalDate START = LocalDate.of(2026, 9, 1);
  public static final List<String> IDS =
      List.of("2564951", "266143", "126760", "126081", "127784", "126144", "126083", "127537");
  private static final List<String> NAMES =
      List.of("감천문화마을", "해운대해수욕장", "광안리해수욕장", "해동용궁사", "자갈치시장", "태종대", "송정해수욕장", "용두산공원");

  private TravelPlanFixtures() {}

  public static Travel travel() {
    return Travel.builder()
        .title("부산 여행")
        .destination("부산")
        .startDate(START)
        .endDate(START.plusDays(2))
        .status(TravelStatus.PLANNED)
        .accommodationArea("해운대")
        .build();
  }

  public static AiTravelPlanGenerateReqDto request() {
    return request(
        IntStream.range(0, 8)
            .mapToObj(
                i ->
                    new WizardPickedPlaceReqDto(
                        "GOOGLE",
                        IDS.get(i),
                        NAMES.get(i),
                        "부산 원본 주소 " + i,
                        35.1 + i / 100.0,
                        129.0 + i / 100.0,
                        "TOURIST_SPOT"))
            .toList());
  }

  public static AiTravelPlanGenerateReqDto request(List<WizardPickedPlaceReqDto> places) {
    return new AiTravelPlanGenerateReqDto(
        places,
        List.of("milmyeon", "dwaeji", "haemul"),
        "BALANCED",
        List.of("자연·힐링", "미식·맛집"),
        "파라다이스 호텔 부산",
        List.of("haeundae"));
  }

  public static TravelPlanAiResponse response(List<String> ids) {
    return new TravelPlanAiResponse(
        IntStream.range(0, 3)
            .mapToObj(
                day -> {
                  int from = day * ids.size() / 3;
                  int to = (day + 1) * ids.size() / 3;
                  return new TravelPlanAiResponse.Day(
                      START.plusDays(day),
                      IntStream.range(from, to)
                          .mapToObj(
                              i ->
                                  new TravelPlanAiResponse.Place(
                                      i - from + 1, "GOOGLE", ids.get(i), "주변 동선을 고려한 추천"))
                          .toList());
                })
            .toList());
  }
}
