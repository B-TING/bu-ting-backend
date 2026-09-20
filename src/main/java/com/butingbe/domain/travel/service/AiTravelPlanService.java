package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.ai.PlaceKey;
import com.butingbe.domain.travel.ai.SelectedPlaceCatalog;
import com.butingbe.domain.travel.ai.TravelPlanAiResponse;
import com.butingbe.domain.travel.ai.TravelPlanCandidateFiller;
import com.butingbe.domain.travel.ai.TravelPlanGenerator;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto.PlanDayResDto;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.PlanPlaceSource;
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
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AiTravelPlanService {
  private final TravelRepository travelRepository;
  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final UserRepository userRepository;
  private final TravelMemberAuthorization authorization;
  private final TravelPlanGenerator generator;
  private final TravelPlanCandidateFiller candidateFiller;
  private final TransactionTemplate transactionTemplate;

  public TravelPlansResDto generate(
      AuthenticatedUser authenticatedUser, UUID travelId, AiTravelPlanGenerateReqDto request) {
    User user =
        userRepository
            .findById(authenticatedUser.id())
            .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found"));
    Travel travel =
        travelRepository
            .findById(travelId)
            .orElseThrow(() -> new ResourceNotFoundException("error.travel.not_found"));
    authorization.validateMember(travelId, user.getId());

    // 고른 장소가 모자라면 카탈로그에서 후보를 보탠다. 합쳐진 목록이 그대로 검증 경로를 탄다.
    TravelPlanCandidateFiller.FilledPlaces filled = candidateFiller.fill(travel, request);
    Map<PlaceKey, WizardPickedPlaceReqDto> catalog =
        SelectedPlaceCatalog.fromPlaces(filled.places());

    // AI 호출은 수 초에서 수십 초 걸린다. 트랜잭션 안에서 기다리면 그동안 DB 커넥션을 쥐고 있어,
    // 동시 요청 몇 건만으로 풀이 말라 이 API와 무관한 요청까지 멈춘다. 저장만 트랜잭션으로 묶는다.
    TravelPlanAiResponse response = generator.generate(travel, request, catalog);

    return transactionTemplate.execute(status -> saveDays(travel, response, catalog, filled));
  }

  private TravelPlansResDto saveDays(
      Travel travel,
      TravelPlanAiResponse response,
      Map<PlaceKey, WizardPickedPlaceReqDto> catalog,
      TravelPlanCandidateFiller.FilledPlaces filled) {
    if (response.days().stream()
        .anyMatch(
            day -> planRepository.existsByTravel_IdAndVisitDate(travel.getId(), day.date()))) {
      throw new ConflictException("error.travel.plan.date_conflict");
    }
    List<Plan> plans =
        response.days().stream()
            .sorted(java.util.Comparator.comparing(TravelPlanAiResponse.Day::date))
            .map(day -> saveDay(travel, day, catalog, filled))
            .toList();
    return TravelPlansResDto.of(travel, plans.stream().map(this::toDay).toList());
  }

  private Plan saveDay(
      Travel travel,
      TravelPlanAiResponse.Day day,
      Map<PlaceKey, WizardPickedPlaceReqDto> catalog,
      TravelPlanCandidateFiller.FilledPlaces filled) {
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
              .contentTypeId(found.type())
              .memo(place.memo())
              .source(
                  filled.autoFilled(key.providerPlaceId())
                      ? PlanPlaceSource.AUTO_FILLED
                      : PlanPlaceSource.USER_PICKED)
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
