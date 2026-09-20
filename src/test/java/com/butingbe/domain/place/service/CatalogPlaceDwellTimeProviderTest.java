package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogPlaceDwellTimeProviderTest {

  @Mock private PlaceRepository placeRepository;
  @InjectMocks private CatalogPlaceDwellTimeProvider provider;

  @Test
  @DisplayName("카탈로그에 있는 체류 시간을 쓴다")
  void readsCatalogValue() {
    when(placeRepository.findByProviderAndProviderPlaceId(any(), any()))
        .thenReturn(Optional.of(place(90)));

    assertThat(provider.dwellMinutes("TOUR_API", "126508")).isEqualTo(90);
  }

  @Test
  @DisplayName("카탈로그에 없으면 기본값으로 답한다")
  void fallsBackWhenAbsent() {
    when(placeRepository.findByProviderAndProviderPlaceId(any(), any()))
        .thenReturn(Optional.empty());

    assertThat(provider.dwellMinutes("TOUR_API", "999")).isEqualTo(60);
  }

  @Test
  @DisplayName("적재는 됐지만 체류 시간이 비어 있어도 기본값으로 답한다")
  void fallsBackWhenDwellMinutesNull() {
    when(placeRepository.findByProviderAndProviderPlaceId(any(), any()))
        .thenReturn(Optional.of(place(null)));

    assertThat(provider.dwellMinutes("TOUR_API", "126508")).isEqualTo(60);
  }

  @Test
  @DisplayName("GOOGLE + contentId로 조회해도 TOUR_API 카탈로그 체류 시간을 찾는다")
  void findsTourApiCatalogViaGoogleContract() {
    when(placeRepository.findByProviderAndProviderPlaceId("GOOGLE", "126508"))
        .thenReturn(Optional.empty());
    when(placeRepository.findByProviderAndProviderPlaceId("TOUR_API", "126508"))
        .thenReturn(Optional.of(place(90)));

    assertThat(provider.dwellMinutes("GOOGLE", "126508")).isEqualTo(90);
  }

  private Place place(Integer dwellMinutes) {
    return Place.builder()
        .provider("TOUR_API")
        .providerPlaceId("126508")
        .name("감천문화마을")
        .dwellMinutes(dwellMinutes)
        .build();
  }
}
