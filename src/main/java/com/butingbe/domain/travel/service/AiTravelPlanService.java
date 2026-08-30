package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.ai.PlaceKey;
import com.butingbe.domain.travel.ai.SelectedPlaceCatalog;
import com.butingbe.domain.travel.ai.TravelPlanAiResponse;
import com.butingbe.domain.travel.ai.TravelPlanGenerator;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto.PlanDayResDto;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
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
  private final TravelPlanGenerator generator;

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

    Map<PlaceKey, WizardPickedPlaceReqDto> catalog = SelectedPlaceCatalog.from(request);
    TravelPlanAiResponse response = generator.generate(travel, request, catalog);
    if (response.days().stream()
        .anyMatch(day -> planRepository.existsByTravel_IdAndVisitDate(travelId, day.date()))) {
      throw new ConflictException("Travel plans already exist for one or more dates.");
    }
    List<Plan> plans =
        response.days().stream()
            .sorted(java.util.Comparator.comparing(TravelPlanAiResponse.Day::date))
            .map(day -> saveDay(travel, day, catalog))
            .toList();
    return TravelPlansResDto.of(travel, plans.stream().map(this::toDay).toList());
  }

  private Plan saveDay(
      Travel travel, TravelPlanAiResponse.Day day, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
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
      PlaceKey key = PlaceKey.of(place.provider(), place.providerPlaceId());
      WizardPickedPlaceReqDto found = catalog.get(key);
      planPlaceRepository.save(
          PlanPlace.builder()
              .plan(plan)
              .sequence(place.order())
              .placeName(found.placeName())
              .address(found.address())
              .latitude(found.latitude())
              .longitude(found.longitude())
              .provider(key.provider())
              .providerPlaceId(key.providerPlaceId())
              .memo(place.memo())
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
