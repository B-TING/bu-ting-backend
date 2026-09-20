package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.entity.PlaceTimeSlot;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogPlaceTimeSlotProviderTest {

  @Mock private PlaceRepository placeRepository;
  @InjectMocks private CatalogPlaceTimeSlotProvider provider;

  @Test
  @DisplayName("보정된 시간대를 읽는다")
  void readsCuratedTimeSlot() {
    when(placeRepository.findByProviderAndProviderPlaceId(any(), any()))
        .thenReturn(Optional.of(place(PlaceTimeSlot.EVENING)));

    assertThat(provider.timeSlot("TOUR_API", "264337")).contains(PlaceTimeSlot.EVENING);
  }

  @Test
  @DisplayName("시간대가 지정되지 않았으면 비어 있다")
  void emptyWhenNotCurated() {
    when(placeRepository.findByProviderAndProviderPlaceId(any(), any()))
        .thenReturn(Optional.of(place(null)));

    assertThat(provider.timeSlot("TOUR_API", "264337")).isEmpty();
  }

  @Test
  @DisplayName("카탈로그에 없는 장소도 비어 있다")
  void emptyWhenPlaceAbsent() {
    when(placeRepository.findByProviderAndProviderPlaceId(any(), any()))
        .thenReturn(Optional.empty());

    assertThat(provider.timeSlot("GOOGLE", "unknown")).isEmpty();
  }

  @Test
  @DisplayName("GOOGLE + contentId로 조회해도 TOUR_API 카탈로그 시간대를 찾는다")
  void findsTourApiCatalogViaGoogleContract() {
    when(placeRepository.findByProviderAndProviderPlaceId("GOOGLE", "264337"))
        .thenReturn(Optional.empty());
    when(placeRepository.findByProviderAndProviderPlaceId("TOUR_API", "264337"))
        .thenReturn(Optional.of(place(PlaceTimeSlot.EVENING)));

    assertThat(provider.timeSlot("GOOGLE", "264337")).contains(PlaceTimeSlot.EVENING);
  }

  private Place place(PlaceTimeSlot timeSlot) {
    return Place.builder()
        .provider("TOUR_API")
        .providerPlaceId("264337")
        .name("광안리 해수욕장")
        .preferredTimeSlot(timeSlot)
        .build();
  }
}
