package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.route.repository.PlaceTravelTimeRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteCacheEvictionSchedulerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 4, 30);

  @Mock private PlaceTravelTimeRepository repository;

  @Test
  @DisplayName("TTL이 지난 시점을 기준으로 삭제한다")
  void deletesEntriesOlderThanTtl() {
    when(repository.deleteFetchedBefore(any())).thenReturn(12);

    new RouteCacheEvictionScheduler(repository, 30).evictExpired(NOW);

    ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).deleteFetchedBefore(threshold.capture());
    assertThat(threshold.getValue()).isEqualTo(NOW.minusDays(30));
  }

  @Test
  @DisplayName("TTL 설정이 바뀌면 기준 시각도 따라간다")
  void usesConfiguredTtl() {
    when(repository.deleteFetchedBefore(any())).thenReturn(0);

    new RouteCacheEvictionScheduler(repository, 7).evictExpired(NOW);

    ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).deleteFetchedBefore(threshold.capture());
    assertThat(threshold.getValue()).isEqualTo(NOW.minusDays(7));
  }

  @Test
  @DisplayName("정리가 실패해도 예외를 밖으로 던지지 않는다")
  void swallowsFailure() {
    when(repository.deleteFetchedBefore(any())).thenThrow(new IllegalStateException("db down"));

    assertThatCode(() -> new RouteCacheEvictionScheduler(repository, 30).evictExpired(NOW))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("주기 진입점은 현재 시각으로 정리를 호출한다")
  void dailyEntryPointRuns() {
    when(repository.deleteFetchedBefore(any())).thenReturn(0);

    new RouteCacheEvictionScheduler(repository, 30).evictExpiredDaily();

    verify(repository).deleteFetchedBefore(any());
  }
}
