package com.butingbe.domain.travel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TravelStatusSchedulerTest {

  @Mock private TravelRepository travelRepository;

  @InjectMocks private TravelStatusScheduler travelStatusScheduler;

  @Test
  @DisplayName("지정한 날짜 기준으로 끝난 여행은 완료로, 시작한 여행은 진행중으로 바꾼다")
  void updateTravelStatusesMovesEndedAndStartedTravels() {
    LocalDate today = LocalDate.of(2026, 9, 5);

    travelStatusScheduler.updateTravelStatuses(today);

    verify(travelRepository)
        .completeEndedTravels(
            eq(today),
            eq(List.of(TravelStatus.PLANNED, TravelStatus.IN_PROGRESS)),
            eq(TravelStatus.COMPLETED));
    verify(travelRepository)
        .startPlannedTravels(eq(today), eq(TravelStatus.PLANNED), eq(TravelStatus.IN_PROGRESS));
  }

  @Test
  @DisplayName("일일 스케줄은 오늘 날짜로 상태 갱신을 실행한다")
  void dailyJobRunsWithToday() {
    travelStatusScheduler.updateTravelStatusesDaily();

    verify(travelRepository)
        .completeEndedTravels(any(LocalDate.class), any(), eq(TravelStatus.COMPLETED));
    verify(travelRepository)
        .startPlannedTravels(
            any(LocalDate.class), eq(TravelStatus.PLANNED), eq(TravelStatus.IN_PROGRESS));
  }
}
