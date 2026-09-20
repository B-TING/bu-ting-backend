package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.place.dto.response.GooglePlaceInfoResDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceEnrichResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class PlaceEnrichmentServiceTest {

  private static final AuthenticatedUser ADMIN =
      new AuthenticatedUser(
          UUID.randomUUID(),
          "admin@example.com",
          "admin",
          List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

  @Mock private PlaceService placeService;
  @Mock private PlaceRepository placeRepository;
  @Mock private OperatorAuthorization operatorAuthorization;
  @InjectMocks private PlaceEnrichmentService placeEnrichmentService;

  @Test
  @DisplayName("평점과 리뷰 수를 채우고 보강 시각을 남긴다")
  void enrichesPopularity() {
    Place place = place("126508");
    when(placeRepository.findEnrichmentTargets(any())).thenReturn(List.of(place));
    when(placeService.getPlaceDetail(any(), any(), any())).thenReturn(detail(4.5, 18420));

    PlaceEnrichResDto result = placeEnrichmentService.enrich(ADMIN, 10);

    assertThat(place.getRating()).isEqualByComparingTo("4.5");
    assertThat(place.getReviewCount()).isEqualTo(18420);
    assertThat(place.getEnrichedAt()).isNotNull();
    assertThat(result.enriched()).isEqualTo(1);
    assertThat(result.targeted()).isEqualTo(1);
  }

  @Test
  @DisplayName("구글에 평점이 없어도 보강 시각을 남겨 다시 조회하지 않는다")
  void marksPlacesWithoutRating() {
    Place place = place("126508");
    when(placeRepository.findEnrichmentTargets(any())).thenReturn(List.of(place));
    when(placeService.getPlaceDetail(any(), any(), any())).thenReturn(detail(null, null));

    PlaceEnrichResDto result = placeEnrichmentService.enrich(ADMIN, 10);

    assertThat(place.getRating()).isNull();
    assertThat(place.getEnrichedAt()).isNotNull();
    assertThat(result.withoutRating()).isEqualTo(1);
    assertThat(result.enriched()).isZero();
  }

  @Test
  @DisplayName("상세 응답이 없어도 보강 시각을 남긴다")
  void marksPlacesWithoutDetail() {
    Place place = place("126508");
    when(placeRepository.findEnrichmentTargets(any())).thenReturn(List.of(place));
    when(placeService.getPlaceDetail(any(), any(), any())).thenReturn(null);

    PlaceEnrichResDto result = placeEnrichmentService.enrich(ADMIN, 10);

    assertThat(place.getEnrichedAt()).isNotNull();
    assertThat(result.withoutRating()).isEqualTo(1);
  }

  @Test
  @DisplayName("한 장소가 실패해도 나머지를 보강하고 시각을 남기지 않아 재시도된다")
  void continuesAfterFailure() {
    Place failing = place("1");
    Place succeeding = place("2");
    when(placeRepository.findEnrichmentTargets(any())).thenReturn(List.of(failing, succeeding));
    when(placeService.getPlaceDetail(any(), any(), any()))
        .thenThrow(new RestClientException("timeout"))
        .thenReturn(detail(4.2, 100));

    PlaceEnrichResDto result = placeEnrichmentService.enrich(ADMIN, 10);

    assertThat(failing.getEnrichedAt()).isNull();
    assertThat(succeeding.getEnrichedAt()).isNotNull();
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.enriched()).isEqualTo(1);
  }

  @Test
  @DisplayName("limit은 1~300으로 보정하고 기본값은 50이다")
  void clampsLimit() {
    when(placeRepository.findEnrichmentTargets(any())).thenReturn(List.of());

    placeEnrichmentService.enrich(ADMIN, null);
    placeEnrichmentService.enrich(ADMIN, 0);
    placeEnrichmentService.enrich(ADMIN, 9999);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(placeRepository, org.mockito.Mockito.times(3)).findEnrichmentTargets(pageable.capture());
    assertThat(pageable.getAllValues().stream().map(Pageable::getPageSize))
        .containsExactly(50, 1, 300);
  }

  @Test
  @DisplayName("운영자가 아니면 보강하지 않는다")
  void rejectsNonOperator() {
    doThrow(new ForbiddenException("error.operator.forbidden"))
        .when(operatorAuthorization)
        .requireOperator(any());

    assertThatThrownBy(() -> placeEnrichmentService.enrich(ADMIN, 10))
        .isInstanceOf(ForbiddenException.class);
    verify(placeRepository, never()).findEnrichmentTargets(any());
  }

  private Place place(String providerPlaceId) {
    return Place.builder()
        .provider("TOUR_API")
        .providerPlaceId(providerPlaceId)
        .name("감천문화마을")
        .contentTypeId("12")
        .build();
  }

  private PlaceDetailResDto detail(Double rating, Integer reviewCount) {
    return new PlaceDetailResDto(
        "126508",
        "12",
        Map.of(),
        new GooglePlaceInfoResDto("google-id", rating, reviewCount, null, List.of(), List.of()));
  }
}
