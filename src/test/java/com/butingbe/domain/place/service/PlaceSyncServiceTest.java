package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.PlaceResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import com.butingbe.domain.place.dto.response.PlaceSyncResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class PlaceSyncServiceTest {

  private static final AuthenticatedUser ADMIN =
      new AuthenticatedUser(
          UUID.randomUUID(),
          "admin@example.com",
          "admin",
          List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

  @Mock private PlaceService placeService;
  @Mock private PlaceRepository placeRepository;
  @Mock private OperatorAuthorization operatorAuthorization;
  @InjectMocks private PlaceSyncService placeSyncService;

  @Test
  @DisplayName("새 장소는 저장하고 구·군 코드로 권역을 채운다")
  void createsNewPlacesWithZone() {
    when(placeService.searchPlaces(any())).thenReturn(page(List.of(item("126508", "26380")), 1));
    when(placeRepository.findByProviderAndProviderPlaceId(any(), eq("126508")))
        .thenReturn(Optional.empty());

    PlaceSyncResDto result = placeSyncService.sync(ADMIN);

    ArgumentCaptor<Place> saved = ArgumentCaptor.forClass(Place.class);
    verify(placeRepository).save(saved.capture());
    assertThat(saved.getValue().getProviderPlaceId()).isEqualTo("126508");
    assertThat(saved.getValue().getZoneId()).isEqualTo(ChatZone.WESTERN_BUSAN);
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.updated()).isZero();
  }

  @Test
  @DisplayName("이미 있는 장소는 새로 저장하지 않고 갱신한다")
  void updatesExistingPlace() {
    when(placeService.searchPlaces(any())).thenReturn(page(List.of(item("126508", "26380")), 1));
    Place existing =
        Place.builder()
            .provider("TOUR_API")
            .providerPlaceId("126508")
            .name("옛 이름")
            .dwellMinutes(90)
            .build();
    when(placeRepository.findByProviderAndProviderPlaceId(any(), eq("126508")))
        .thenReturn(Optional.of(existing));

    PlaceSyncResDto result = placeSyncService.sync(ADMIN);

    verify(placeRepository, never()).save(any());
    assertThat(existing.getName()).isEqualTo("감천문화마을");
    assertThat(existing.getZoneId()).isEqualTo(ChatZone.WESTERN_BUSAN);
    // 보강 컬럼은 동기화가 덮지 않는다
    assertThat(existing.getDwellMinutes()).isEqualTo(90);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.created()).isZero();
  }

  @Test
  @DisplayName("부산 밖 코드나 코드 없음은 권역을 비워 둔다")
  void leavesZoneEmptyForUnknownDistrict() {
    when(placeService.searchPlaces(any())).thenReturn(page(List.of(item("1", "11110")), 1));
    when(placeRepository.findByProviderAndProviderPlaceId(any(), eq("1")))
        .thenReturn(Optional.empty());

    placeSyncService.sync(ADMIN);

    ArgumentCaptor<Place> saved = ArgumentCaptor.forClass(Place.class);
    verify(placeRepository).save(saved.capture());
    assertThat(saved.getValue().getZoneId()).isNull();
  }

  @Test
  @DisplayName("contentId나 이름이 없는 항목은 건너뛴다")
  void skipsItemsWithoutIdentity() {
    PlaceResDto noId = new PlaceResDto(null, "12", "이름", null, null, null, null, null, null, null);
    PlaceResDto blankId =
        new PlaceResDto(" ", "12", "이름", null, null, null, null, null, null, null);
    PlaceResDto noTitle =
        new PlaceResDto("126508", "12", null, null, null, null, null, null, null, null);
    when(placeService.searchPlaces(any())).thenReturn(page(List.of(noId, blankId, noTitle), 3));

    PlaceSyncResDto result = placeSyncService.sync(ADMIN);

    verify(placeRepository, never()).save(any());
    assertThat(result.skipped()).isEqualTo(3);
  }

  @Test
  @DisplayName("한 페이지가 실패해도 적재를 계속하고 실패 건수를 보고한다")
  void continuesAfterPageFailure() {
    when(placeService.searchPlaces(any()))
        .thenThrow(new RestClientException("timeout"))
        .thenReturn(page(List.of(item("126508", "26380")), 1));
    when(placeRepository.findByProviderAndProviderPlaceId(any(), eq("126508")))
        .thenReturn(Optional.empty());

    PlaceSyncResDto result = placeSyncService.sync(ADMIN);

    assertThat(result.failedPages()).isEqualTo(1);
    assertThat(result.created()).isEqualTo(1);
  }

  @Test
  @DisplayName("빈 페이지를 만나면 멈춘다")
  void stopsOnEmptyPage() {
    when(placeService.searchPlaces(any())).thenReturn(page(List.of(), 0));

    PlaceSyncResDto result = placeSyncService.sync(ADMIN);

    assertThat(result.created()).isZero();
    assertThat(result.totalCount()).isZero();
    verify(placeService).searchPlaces(any(PlaceSearchReqDto.class));
  }

  @Test
  @DisplayName("운영자가 아니면 적재하지 않는다")
  void rejectsNonOperator() {
    org.mockito.Mockito.doThrow(new ForbiddenException("error.operator.forbidden"))
        .when(operatorAuthorization)
        .requireOperator(any());

    assertThatThrownBy(() -> placeSyncService.sync(ADMIN)).isInstanceOf(ForbiddenException.class);
    verify(placeService, never()).searchPlaces(any());
  }

  private PlaceSearchResDto page(List<PlaceResDto> places, int totalCount) {
    return new PlaceSearchResDto(1, 100, totalCount, places);
  }

  private PlaceResDto item(String contentId, String districtCode) {
    return new PlaceResDto(
        contentId,
        "12",
        "감천문화마을",
        "부산광역시 사하구 감내2로 203",
        "https://example.com/image.jpg",
        "https://example.com/thumb.jpg",
        129.0107,
        35.0975,
        "26",
        districtCode);
  }
}
