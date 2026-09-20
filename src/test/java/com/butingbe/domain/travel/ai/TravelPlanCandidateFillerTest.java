package com.butingbe.domain.travel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.response.PlaceCandidateResDto;
import com.butingbe.domain.place.service.PlaceCandidateFinder;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelPace;
import com.butingbe.domain.travel.entity.TravelStatus;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TravelPlanCandidateFillerTest {

  @Mock private PlaceCandidateFinder placeCandidateFinder;
  @InjectMocks private TravelPlanCandidateFiller filler;

  @Test
  @DisplayName("장소를 하나도 고르지 않으면 일수와 속도만큼 후보로 채운다")
  void fillsEverythingWhenNothingSelected() {
    // 2일 × BALANCED(하루 4곳) = 8곳
    when(placeCandidateFinder.findCandidates(any(), any(), eq(8)))
        .thenReturn(List.of(candidate("1"), candidate("2")));

    var merged = filler.fill(travel(TravelPace.BALANCED, null), request());

    assertThat(merged.places()).hasSize(2);
    assertThat(merged.places().get(0).providerPlaceId()).isEqualTo("1");
    assertThat(merged.places().get(0).provider()).isEqualTo("GOOGLE");
  }

  @Test
  @DisplayName("카탈로그 TOUR_API 후보는 일정 계약용 GOOGLE provider로 바꾼다")
  void mapsTourApiProviderToGoogle() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt()))
        .thenReturn(List.of(candidate("126081")));

    var merged = filler.fill(travel(TravelPace.BALANCED, null), request());

    assertThat(merged.places().get(0).provider()).isEqualTo("GOOGLE");
    assertThat(SelectedPlaceCatalog.fromPlaces(merged.places()))
        .containsKey(PlaceKey.of("GOOGLE", "126081"));
  }

  @Test
  @DisplayName("카탈로그 provider가 비어 있으면 GOOGLE로 본다")
  void mapsBlankCatalogProviderToGoogle() {
    assertThat(TravelPlanCandidateFiller.toPlanProvider(null)).isEqualTo("GOOGLE");
    assertThat(TravelPlanCandidateFiller.toPlanProvider("  ")).isEqualTo("GOOGLE");
  }

  @Test
  @DisplayName("고른 장소는 그대로 두고 부족분만 채운다")
  void fillsOnlyTheGap() {
    when(placeCandidateFinder.findCandidates(any(), any(), eq(7)))
        .thenReturn(List.of(candidate("9")));

    var merged = filler.fill(travel(TravelPace.BALANCED, null), request(picked("user-1")));

    assertThat(merged.places()).hasSize(2);
    assertThat(merged.places().get(0).providerPlaceId()).isEqualTo("user-1");
    assertThat(merged.places().get(1).providerPlaceId()).isEqualTo("9");
  }

  @Test
  @DisplayName("이미 고른 장소는 후보에서 빼달라고 넘긴다")
  void excludesAlreadyPickedPlaces() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

    filler.fill(travel(TravelPace.BALANCED, null), request(picked("user-1"), picked("user-2")));

    ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
    verify(placeCandidateFinder).findCandidates(any(), excluded.capture(), anyInt());
    assertThat(excluded.getValue()).containsExactlyInAnyOrder("user-1", "user-2");
  }

  @Test
  @DisplayName("충분히 골랐으면 후보를 찾지 않는다")
  void skipsLookupWhenEnoughSelected() {
    WizardPickedPlaceReqDto[] picked = new WizardPickedPlaceReqDto[8];
    for (int i = 0; i < picked.length; i++) {
      picked[i] = picked("user-" + i);
    }

    var merged = filler.fill(travel(TravelPace.BALANCED, null), request(picked));

    assertThat(merged.places()).hasSize(8);
    verify(placeCandidateFinder, never()).findCandidates(any(), any(), anyInt());
  }

  @Test
  @DisplayName("여행 속도가 빠르면 더 많이 채운다")
  void fillsMoreForTighterPace() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

    filler.fill(travel(TravelPace.TIGHT, null), request());

    verify(placeCandidateFinder).findCandidates(any(), any(), eq(10));
  }

  @Test
  @DisplayName("숙소 권역이 지정되면 그 권역에서만 고른다")
  void narrowsToAccommodationZone() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

    filler.fill(travel(TravelPace.BALANCED, "SUYEONG_NAMGU"), request());

    verify(placeCandidateFinder)
        .findCandidates(eq(Set.of(ChatZone.SUYEONG_NAMGU)), any(), anyInt());
  }

  @Test
  @DisplayName("모바일이 보내는 위저드 지역 아이디도 권역으로 좁힌다")
  void narrowsToZoneFromWizardAreaId() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

    filler.fill(travel(TravelPace.BALANCED, "haeundae"), request());

    verify(placeCandidateFinder)
        .findCandidates(eq(Set.of(ChatZone.HAEUNDAE_GIJANG)), any(), anyInt());
  }

  @Test
  @DisplayName("해석할 수 없는 숙소 지역은 전체에서 고른다")
  void fallsBackToAllZonesForUnknownArea() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

    filler.fill(travel(TravelPace.BALANCED, "서울"), request());

    verify(placeCandidateFinder).findCandidates(eq(Set.of()), any(), anyInt());
  }

  @Test
  @DisplayName("요청 자체가 없거나 선택 목록이 null이어도 후보로 채운다")
  void fillsWhenRequestOrSelectionMissing() {
    when(placeCandidateFinder.findCandidates(any(), any(), eq(8)))
        .thenReturn(List.of(candidate("1")));

    assertThat(filler.fill(travel(TravelPace.BALANCED, null), null).places()).hasSize(1);
    assertThat(
            filler
                .fill(
                    travel(TravelPace.BALANCED, null),
                    new AiTravelPlanGenerateReqDto(null, null, null, null, null, null))
                .places())
        .hasSize(1);
  }

  @Test
  @DisplayName("여유로운 속도는 하루 세 곳 기준으로 채운다")
  void fillsFewerForRelaxedPace() {
    when(placeCandidateFinder.findCandidates(any(), any(), anyInt())).thenReturn(List.of());

    filler.fill(travel(TravelPace.RELAXED, null), request());

    verify(placeCandidateFinder).findCandidates(any(), any(), eq(6));
  }

  @Test
  @DisplayName("서버가 채운 장소만 출처로 표시한다")
  void marksOnlyAutoFilledPlaces() {
    when(placeCandidateFinder.findCandidates(any(), any(), eq(7)))
        .thenReturn(List.of(candidate("server-1")));

    var merged = filler.fill(travel(TravelPace.BALANCED, null), request(picked("user-1")));

    assertThat(merged.autoFilled("server-1")).isTrue();
    assertThat(merged.autoFilled("user-1")).isFalse();
  }

  @Test
  @DisplayName("채울 필요가 없으면 표시할 후보도 없다")
  void hasNoAutoFilledWhenNothingAdded() {
    WizardPickedPlaceReqDto[] picked = new WizardPickedPlaceReqDto[8];
    for (int i = 0; i < picked.length; i++) {
      picked[i] = picked("user-" + i);
    }

    var merged = filler.fill(travel(TravelPace.BALANCED, null), request(picked));

    assertThat(merged.autoFilledProviderPlaceIds()).isEmpty();
    assertThat(merged.autoFilled("user-0")).isFalse();
  }

  private Travel travel(TravelPace pace, String accommodationArea) {
    return Travel.builder()
        .destination("부산")
        .startDate(TravelPlanFixtures.START)
        .endDate(TravelPlanFixtures.START.plusDays(1))
        .status(TravelStatus.PLANNED)
        .pace(pace)
        .accommodationArea(accommodationArea)
        .build();
  }

  @Test
  @DisplayName("카탈로그 provider 가 비어 있으면 일정 계약의 기본 provider 로 맞춘다")
  void blankCatalogProviderFallsBackToPlanProvider() {
    // 카탈로그에 provider 가 비어 들어온 행이 섞여도 일정 생성 계약(GOOGLE)이 깨지지 않아야 한다.
    assertThat(TravelPlanCandidateFiller.toPlanProvider(null)).isEqualTo("GOOGLE");
    assertThat(TravelPlanCandidateFiller.toPlanProvider("  ")).isEqualTo("GOOGLE");
  }

  private AiTravelPlanGenerateReqDto request(WizardPickedPlaceReqDto... places) {
    return new AiTravelPlanGenerateReqDto(List.of(places), null, null, null, null, null);
  }

  private WizardPickedPlaceReqDto picked(String id) {
    return new WizardPickedPlaceReqDto("GOOGLE", id, "고른 장소", "주소", 35.1, 129.1, "TOURIST_SPOT");
  }

  private PlaceCandidateResDto candidate(String id) {
    return new PlaceCandidateResDto("TOUR_API", id, "후보 장소", "주소", 35.2, 129.2, "12");
  }
}
