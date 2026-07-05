package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto.PlanDayResDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.PlanRoute;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.PlanRouteRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelServiceImpl implements TravelService {

  private final TravelRepository travelRepository;
  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final PlanRouteRepository planRouteRepository;
  private final TravelMemberRepository travelMemberRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public TravelResDto createTravel(AuthenticatedUser authenticatedUser, TravelCreateReqDto request) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelDate(request);

    Travel travel =
        Travel.builder()
            .title(request.title())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .status(TravelStatus.PLANNED)
            .hasHeavyBaggage(request.hasHeavyBaggage())
            .hasPets(request.hasPets())
            .travelStyle(request.travelStyle())
            .preferFlatTerrain(request.preferFlatTerrain())
            .pace(request.pace())
            .companionCount(request.companionCount())
            .preferredFoods(request.preferredFoods())
            .companionTypes(request.companionTypes())
            .accommodationArea(request.accommodationArea())
            .build();

    Travel savedTravel = travelRepository.save(travel);
    travelMemberRepository.save(
        TravelMember.builder().travel(savedTravel).user(user).role(TravelTeamRole.LEADER).build());

    return TravelResDto.from(savedTravel);
  }

  @Override
  public TravelPlansResDto getTravelPlans(AuthenticatedUser authenticatedUser, UUID travelId) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, user.getId());

    Travel travel =
        travelRepository
            .findById(travelId)
            .orElseThrow(() -> new IllegalArgumentException("Travel not found."));
    List<PlanDayResDto> days =
        planRepository.findByTravel_IdOrderByDayNumberAsc(travelId).stream()
            .map(this::toPlanDayResponse)
            .toList();

    return TravelPlansResDto.of(travel, days);
  }

  @Override
  @Transactional
  public PlanResDto createPlan(
      AuthenticatedUser authenticatedUser, UUID travelId, PlanCreateReqDto request) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, user.getId());

    Travel travel = findTravel(travelId);
    validatePlanDate(travel, request);
    validatePlanDayNumber(travelId, request.dayNumber());

    Plan plan =
        Plan.builder()
            .travel(travel)
            .dayNumber(request.dayNumber())
            .visitDate(request.visitDate())
            .build();

    return PlanResDto.from(planRepository.save(plan));
  }

  @Override
  @Transactional
  public PlanPlaceResDto createPlanPlace(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planId,
      PlanPlaceCreateReqDto request) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, user.getId());

    Plan plan =
        planRepository
            .findById(planId)
            .filter(foundPlan -> foundPlan.getTravel().getId().equals(travelId))
            .orElseThrow(() -> new IllegalArgumentException("Plan not found."));
    Integer sequence = resolveSequence(planId, request.sequence());

    PlanPlace planPlace =
        PlanPlace.builder()
            .plan(plan)
            .sequence(sequence)
            .placeName(request.placeName())
            .address(request.address())
            .latitude(request.latitude())
            .longitude(request.longitude())
            .provider(request.provider())
            .providerPlaceId(request.providerPlaceId())
            .durationMinutes(request.durationMinutes())
            .visited(request.visited())
            .build();

    return PlanPlaceResDto.from(planPlaceRepository.save(planPlace));
  }

  @Override
  public List<PlanPlaceResDto> getPlanPlaces(AuthenticatedUser authenticatedUser, UUID planId) {
    User user = findAuthenticatedUser(authenticatedUser);
    Plan plan =
        planRepository
            .findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found."));
    validateTravelMember(plan.getTravel().getId(), user.getId());

    return planPlaceRepository.findByPlan_IdOrderBySequenceAsc(planId).stream()
        .map(PlanPlaceResDto::from)
        .toList();
  }

  @Override
  @Transactional
  public void deletePlanPlace(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planId, UUID planPlaceId) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, user.getId());
    Plan plan = findPlanInTravel(travelId, planId);
    PlanPlace planPlace =
        planPlaceRepository
            .findById(planPlaceId)
            .filter(foundPlace -> foundPlace.getPlan().getId().equals(plan.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Plan place not found."));

    Integer deletedSequence = planPlace.getSequence();

    planRouteRepository.deleteByPlan_Id(planId);
    planPlaceRepository.delete(planPlace);
    planPlaceRepository.flush();
    decreaseSequencesAfterDeletedPlace(planId, deletedSequence);
  }

  private PlanDayResDto toPlanDayResponse(Plan plan) {
    Map<UUID, PlanRoute> routeByFromPlaceId =
        planRouteRepository.findByPlan_Id(plan.getId()).stream()
            .collect(Collectors.toMap(route -> route.getFromPlace().getId(), Function.identity()));

    return PlanDayResDto.of(
        plan, planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.getId()), routeByFromPlaceId);
  }

  private User findAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    return userRepository
        .findById(authenticatedUser.id())
        .orElseThrow(UnauthenticatedException::new);
  }

  private Travel findTravel(UUID travelId) {
    return travelRepository
        .findById(travelId)
        .orElseThrow(() -> new IllegalArgumentException("Travel not found."));
  }

  private Plan findPlanInTravel(UUID travelId, UUID planId) {
    return planRepository
        .findById(planId)
        .filter(foundPlan -> foundPlan.getTravel().getId().equals(travelId))
        .orElseThrow(() -> new IllegalArgumentException("Plan not found."));
  }

  private void validateTravelMember(UUID travelId, UUID userId) {
    if (!travelMemberRepository.existsByTravel_IdAndUser_Id(travelId, userId)) {
      throw new IllegalArgumentException("User is not a travel member.");
    }
  }

  private void validateTravelDate(TravelCreateReqDto request) {
    if (request.endDate().isBefore(request.startDate())) {
      throw new IllegalArgumentException("Travel end date cannot be before start date.");
    }
  }

  private void validatePlanDate(Travel travel, PlanCreateReqDto request) {
    if (request.visitDate().isBefore(travel.getStartDate())
        || request.visitDate().isAfter(travel.getEndDate())) {
      throw new IllegalArgumentException("Plan visit date must be within the travel period.");
    }
  }

  private void validatePlanDayNumber(UUID travelId, Integer dayNumber) {
    if (planRepository.existsByTravel_IdAndDayNumber(travelId, dayNumber)) {
      throw new IllegalArgumentException("Plan day number already exists.");
    }
  }

  private Integer resolveSequence(UUID planId, Integer requestedSequence) {
    Integer sequence =
        requestedSequence != null
            ? requestedSequence
            : planPlaceRepository
                .findTopByPlan_IdOrderBySequenceDesc(planId)
                .map(place -> place.getSequence() + 1)
                .orElse(1);

    if (planPlaceRepository.existsByPlan_IdAndSequence(planId, sequence)) {
      throw new IllegalArgumentException("Plan place sequence already exists.");
    }

    return sequence;
  }

  private void decreaseSequencesAfterDeletedPlace(UUID planId, Integer deletedSequence) {
    planPlaceRepository
        .findByPlan_IdAndSequenceGreaterThanOrderBySequenceAsc(planId, deletedSequence)
        .forEach(place -> place.changeSequence(place.getSequence() - 1));
  }
}
