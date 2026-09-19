package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.response.PlaceCandidateResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogPlaceCandidateFinderTest {

  @Mock private PlaceRepository placeRepository;
  @InjectMocks private CatalogPlaceCandidateFinder finder;

  @Test
  @DisplayName("평점과 리뷰 수를 함께 본다 — 리뷰 적은 만점보다 리뷰 많은 고평점이 위다")
  void ranksByRatingAndReviewCount() {
    Place fewReviewsPerfect = place("1", BigDecimal.valueOf(5.0), 3);
    Place manyReviewsGood = place("2", BigDecimal.valueOf(4.4), 20000);
    when(placeRepository.findCandidates(any(), any()))
        .thenReturn(List.of(fewReviewsPerfect, manyReviewsGood));

    List<PlaceCandidateResDto> candidates = finder.findCandidates(Set.of(), Set.of(), 2);

    assertThat(candidates)
        .extracting(PlaceCandidateResDto::providerPlaceId)
        .containsExactly("2", "1");
  }

  @Test
  @DisplayName("이미 일정에 있는 장소는 빼고 limit만큼만 준다")
  void excludesPickedPlacesAndRespectsLimit() {
    when(placeRepository.findCandidates(any(), any()))
        .thenReturn(
            List.of(
                place("1", BigDecimal.valueOf(4.5), 100),
                place("2", BigDecimal.valueOf(4.4), 90),
                place("3", BigDecimal.valueOf(4.3), 80)));

    List<PlaceCandidateResDto> candidates = finder.findCandidates(Set.of(), Set.of("1"), 1);

    assertThat(candidates).extracting(PlaceCandidateResDto::providerPlaceId).containsExactly("2");
  }

  @Test
  @DisplayName("평점이나 리뷰 수가 없어도 후보에서 빠지지 않는다")
  void keepsPlacesWithoutPopularity() {
    when(placeRepository.findCandidates(any(), any())).thenReturn(List.of(place("1", null, null)));

    assertThat(finder.findCandidates(Set.of(), Set.of(), 1)).hasSize(1);
  }

  @Test
  @DisplayName("권역이 비면 필터 없이 전체에서 고른다")
  void passesNullZoneFilterWhenNoZoneGiven() {
    when(placeRepository.findCandidates(isNull(), any())).thenReturn(List.of());

    finder.findCandidates(null, null, 3);

    verify(placeRepository).findCandidates(isNull(), any());
  }

  @Test
  @DisplayName("권역이 지정되면 그대로 넘긴다")
  void passesZoneFilter() {
    when(placeRepository.findCandidates(eq(Set.of(ChatZone.YEONGDO)), any())).thenReturn(List.of());

    finder.findCandidates(Set.of(ChatZone.YEONGDO), Set.of(), 3);

    verify(placeRepository).findCandidates(eq(Set.of(ChatZone.YEONGDO)), any());
  }

  @Test
  @DisplayName("limit이 1보다 작으면 조회하지 않는다")
  void skipsLookupForNonPositiveLimit() {
    assertThat(finder.findCandidates(Set.of(), Set.of(), 0)).isEmpty();

    verify(placeRepository, never()).findCandidates(any(), any());
  }

  @Test
  @DisplayName("주소가 비면 이름으로 대신한다")
  void fallsBackToNameWhenAddressBlank() {
    Place noAddress =
        Place.builder()
            .provider("TOUR_API")
            .providerPlaceId("1")
            .name("감천문화마을")
            .address("  ")
            .latitude(35.0975)
            .longitude(129.0107)
            .build();
    when(placeRepository.findCandidates(any(), any())).thenReturn(List.of(noAddress));

    assertThat(finder.findCandidates(Set.of(), Set.of(), 1).get(0).address()).isEqualTo("감천문화마을");
  }

  private Place place(String providerPlaceId, BigDecimal rating, Integer reviewCount) {
    return Place.builder()
        .provider("TOUR_API")
        .providerPlaceId(providerPlaceId)
        .name("장소" + providerPlaceId)
        .address("부산광역시")
        .latitude(35.1)
        .longitude(129.1)
        .contentTypeId("12")
        .rating(rating)
        .reviewCount(reviewCount)
        .build();
  }
}
