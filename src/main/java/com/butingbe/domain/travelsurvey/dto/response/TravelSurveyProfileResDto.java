package com.butingbe.domain.travelsurvey.dto.response;

import com.butingbe.domain.travelsurvey.entity.TravelSurvey;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record TravelSurveyProfileResDto(
    String preferredLanguage,
    Boolean isPlanned,
    Boolean isRelaxed,
    Boolean isSolo,
    Boolean isLight,
    Boolean isFamiliar,
    List<String> purposes,
    List<Integer> skippedSteps,
    boolean skippedAll,
    LocalDateTime completedAt,
    String aiPromptContext) {

  public static TravelSurveyProfileResDto from(TravelSurvey survey) {
    return new TravelSurveyProfileResDto(
        survey.getPreferredLanguage(),
        survey.getPlanned(),
        survey.getRelaxed(),
        survey.getSolo(),
        survey.getLight(),
        survey.getFamiliar(),
        Arrays.asList(survey.getPurposes()),
        Arrays.asList(survey.getSkippedSteps()),
        survey.isSkippedAll(),
        survey.getCompletedAt(),
        survey.getAiPromptContext());
  }
}
