package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.ai.TravelPlanAiClient;
import com.butingbe.domain.travel.ai.TravelPlanAiResponse;
import com.butingbe.domain.travel.ai.TravelPlanAiResponseValidator;
import com.butingbe.domain.travel.ai.TravelPlanPromptBuilder;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto.PlanDayResDto;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiTravelPlanService {
  private final TravelRepository travelRepository;
  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final UserRepository userRepository;
  private final TravelMemberAuthorization authorization;
  private final TravelPlanPromptBuilder promptBuilder;
  private final TravelPlanAiClient aiClient;
  private final TravelPlanAiResponseValidator validator;

  @Transactional
  public TravelPlansResDto generate(
      AuthenticatedUser authenticatedUser, UUID travelId, AiTravelPlanGenerateReqDto request) {
    User user =
        userRepository
            .findById(authenticatedUser.id())
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    Travel travel =
        travelRepository
            .findById(travelId)
            .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
    authorization.validateMember(travelId, user.getId());

    TravelPlanAiResponse response = aiClient.generate(promptBuilder.build(travel, request));
    validator.validate(travel, response);
    List<Plan> plans = response.days().stream().map(day -> saveDay(travel, day, request)).toList();
    return TravelPlansResDto.of(travel, plans.stream().map(this::toDay).toList());
  }

  private Plan saveDay(
      Travel travel, TravelPlanAiResponse.Day day, AiTravelPlanGenerateReqDto request) {
    Plan plan =
        planRepository.save(
            Plan.builder()
                .travel(travel)
                .dayNumber(
                    (int)
                        (java.time.temporal.ChronoUnit.DAYS.between(
                                travel.getStartDate(), day.date())
                            + 1))
                .visitDate(day.date())
                .build());
    for (TravelPlanAiResponse.Place place : day.places()) {
      AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto found =
          request.selectedPlaces().stream()
              .filter(candidate -> candidate.placeName().equals(place.name()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "AI selected an unknown place: " + place.name()));
      planPlaceRepository.save(
          PlanPlace.builder()
              .plan(plan)
              .sequence(place.order())
              .placeName(found.placeName())
              .address(found.address())
              .latitude(found.latitude())
              .longitude(found.longitude())
              .provider(PlaceProvider.valueOf(found.provider().toUpperCase()))
              .providerPlaceId(found.providerPlaceId())
              .memo(place.description() + "\n" + place.recommendationReason())
              .build());
    }
    return plan;
  }

  private PlanDayResDto toDay(Plan plan) {
    return new PlanDayResDto(
        plan.getId(),
        plan.getDayNumber(),
        plan.getVisitDate(),
        planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.getId()).stream()
            .map(
                place ->
                    com.butingbe.domain.travel.dto.response.TravelPlansResDto.PlanPlaceResDto.of(
                        place, null))
            .toList());
  }
}
