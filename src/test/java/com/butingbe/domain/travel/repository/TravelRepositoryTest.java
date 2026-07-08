package com.butingbe.domain.travel.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.service.TravelStatusScheduler;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelRepositoryTest extends AbstractContainerTest {

  @Autowired private TravelRepository travelRepository;
  @Autowired private PlanRepository planRepository;
  @Autowired private PlanPlaceRepository planPlaceRepository;
  @Autowired private TravelStatusScheduler travelStatusScheduler;

  @Test
  @DisplayName("start date가 오늘이거나 지났고 종료일이 지나지 않은 PLANNED 여행은 IN_PROGRESS로 변경된다")
  void updateTravelStatusesChangesPlannedToInProgress() {
    Travel travel =
        travelRepository.save(
            createTravel(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), TravelStatus.PLANNED));

    travelStatusScheduler.updateTravelStatuses(LocalDate.of(2026, 8, 1));

    assertThat(travelRepository.findById(travel.getId()).orElseThrow().getStatus())
        .isEqualTo(TravelStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("end date가 지난 PLANNED 또는 IN_PROGRESS 여행은 COMPLETED로 변경된다")
  void updateTravelStatusesChangesEndedTravelsToCompleted() {
    Travel planned =
        travelRepository.save(
            createTravel(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), TravelStatus.PLANNED));
    Travel inProgress =
        travelRepository.save(
            createTravel(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), TravelStatus.IN_PROGRESS));

    travelStatusScheduler.updateTravelStatuses(LocalDate.of(2026, 8, 4));

    assertThat(travelRepository.findById(planned.getId()).orElseThrow().getStatus())
        .isEqualTo(TravelStatus.COMPLETED);
    assertThat(travelRepository.findById(inProgress.getId()).orElseThrow().getStatus())
        .isEqualTo(TravelStatus.COMPLETED);
  }

  @Test
  @DisplayName("travel id로 plan 목록을 dayNumber 오름차순으로 조회한다")
  void findPlansByTravelIdOrderByDayNumberAsc() {
    Travel travel = travelRepository.save(createTravel());
    Plan day2 =
        planRepository.save(
            Plan.builder().travel(travel).dayNumber(2).visitDate(LocalDate.of(2026, 8, 2)).build());
    Plan day1 =
        planRepository.save(
            Plan.builder().travel(travel).dayNumber(1).visitDate(LocalDate.of(2026, 8, 1)).build());

    List<Plan> plans = planRepository.findByTravel_IdOrderByDayNumberAsc(travel.getId());

    assertThat(plans).extracting(Plan::getId).containsExactly(day1.getId(), day2.getId());
  }

  @Test
  @DisplayName("plan id로 장소 목록을 sequence 오름차순으로 조회한다")
  void findPlacesByPlanIdOrderBySequenceAsc() {
    Travel travel = travelRepository.save(createTravel());
    Plan plan =
        planRepository.save(
            Plan.builder().travel(travel).dayNumber(1).visitDate(LocalDate.of(2026, 8, 1)).build());
    PlanPlace second = planPlaceRepository.save(createPlace(plan, 2, "Haeundae"));
    PlanPlace first = planPlaceRepository.save(createPlace(plan, 1, "Busan Station"));

    List<PlanPlace> places = planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.getId());

    assertThat(places).extracting(PlanPlace::getId).containsExactly(first.getId(), second.getId());
  }

  private Travel createTravel() {
    return createTravel(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), TravelStatus.PLANNED);
  }

  private Travel createTravel(LocalDate startDate, LocalDate endDate, TravelStatus status) {
    return Travel.builder()
        .title("Busan")
        .startDate(startDate)
        .endDate(endDate)
        .status(status)
        .build();
  }

  private PlanPlace createPlace(Plan plan, Integer sequence, String placeName) {
    return PlanPlace.builder()
        .plan(plan)
        .sequence(sequence)
        .placeName(placeName)
        .address("Busan")
        .latitude(35.115)
        .longitude(129.041)
        .provider(PlaceProvider.GOOGLE)
        .providerPlaceId(placeName.toLowerCase().replace(" ", "-"))
        .durationMinutes(30)
        .build();
  }
}
