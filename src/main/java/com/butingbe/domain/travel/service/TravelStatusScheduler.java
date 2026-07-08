package com.butingbe.domain.travel.service;

import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TravelStatusScheduler {

  private static final String SEOUL_ZONE = "Asia/Seoul";

  private final TravelRepository travelRepository;
  private final Clock clock = Clock.system(java.time.ZoneId.of(SEOUL_ZONE));

  @Scheduled(cron = "${travel.status.scheduler.cron:0 5 0 * * *}", zone = SEOUL_ZONE)
  @Transactional
  public void updateTravelStatusesDaily() {
    updateTravelStatuses(LocalDate.now(clock));
  }

  @Transactional
  public void updateTravelStatuses(LocalDate today) {
    travelRepository.completeEndedTravels(
        today,
        List.of(TravelStatus.PLANNED, TravelStatus.IN_PROGRESS),
        TravelStatus.COMPLETED);
    travelRepository.startPlannedTravels(today, TravelStatus.PLANNED, TravelStatus.IN_PROGRESS);
  }
}
