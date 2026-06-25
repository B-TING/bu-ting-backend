package com.butingbe.domain.travelsurvey.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

public record TravelSurveyProfileReqDto(
    @Size(max = 5, message = "preferredLanguage must be 5 characters or less")
        String preferredLanguage,
    Boolean isPlanned,
    Boolean isRelaxed,
    Boolean isSolo,
    Boolean isLight,
    Boolean isFamiliar,
    List<@Size(max = 30, message = "purpose must be 30 characters or less") String> purposes,
    List<Integer> skippedSteps,
    boolean skippedAll) {

  public String normalizedPreferredLanguage() {
    if (preferredLanguage == null || preferredLanguage.isBlank()) {
      return "ko";
    }
    return preferredLanguage;
  }

  public String[] purposesArray() {
    if (purposes == null) {
      return new String[0];
    }
    return purposes.toArray(String[]::new);
  }

  public Integer[] skippedStepsArray() {
    if (skippedSteps == null) {
      return new Integer[0];
    }
    return skippedSteps.toArray(Integer[]::new);
  }
}
