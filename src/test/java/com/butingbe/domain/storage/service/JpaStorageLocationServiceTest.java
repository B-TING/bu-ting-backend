package com.butingbe.domain.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.butingbe.domain.station.entity.Station;
import com.butingbe.domain.storage.dto.StorageLocationSearchReqDto;
import com.butingbe.domain.storage.entity.StorageLocation;
import com.butingbe.domain.storage.repository.StorageLocationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaStorageLocationServiceTest {
  @Mock private StorageLocationRepository repository;
  @InjectMocks private JpaStorageLocationService service;

  @Test
  void returnsLocationsWithinRadiusSortedByDistance() {
    StorageLocation nearby = location(UUID.randomUUID(), 35.1796, 129.0756);
    StorageLocation far = location(UUID.randomUUID(), 35.2000, 129.1000);
    when(repository.findAll()).thenReturn(List.of(far, nearby));

    var result = service.search(new StorageLocationSearchReqDto(35.1796, 129.0756, 1_000));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().distanceMeters()).isZero();
  }

  @Test
  void returnsEmptyWhenNoLocationIsWithinRadius() {
    StorageLocation far = location(UUID.randomUUID(), 35.2000, 129.1000);
    when(repository.findAll()).thenReturn(List.of(far));

    var result = service.search(new StorageLocationSearchReqDto(35.1796, 129.0756, 1));

    assertThat(result).isEmpty();
  }

  private StorageLocation location(UUID id, double latitude, double longitude) {
    Station station = mock(Station.class);
    when(station.getLine()).thenReturn("1호선");
    when(station.getName()).thenReturn("다대포해수욕장");
    when(station.getLatitude()).thenReturn(java.math.BigDecimal.valueOf(latitude));
    when(station.getLongitude()).thenReturn(java.math.BigDecimal.valueOf(longitude));
    return StorageLocation.builder()
        .id(id)
        .station(station)
        .locationDetail("대합실")
        .smallCount(10)
        .mediumCount(8)
        .largeCount(0)
        .extraLargeCount(4)
        .costRaw("소: 2,000원(3시간당)")
        .company("위드락커")
        .build();
  }
}
