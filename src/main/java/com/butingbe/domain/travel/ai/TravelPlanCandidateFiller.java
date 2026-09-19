package com.butingbe.domain.travel.ai;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.response.PlaceCandidateResDto;
import com.butingbe.domain.place.entity.AccommodationAreaZones;
import com.butingbe.domain.place.service.PlaceCandidateFinder;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelPace;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사용자가 고른 장소가 일정을 채우기에 모자라면 카탈로그에서 후보를 보탠다.
 *
 * <p>위저드에서 장소를 하나도 고르지 않아도 일정이 나오게 하려는 것이다. 고른 장소는 그대로 두고 부족한 만큼만 더한다.
 *
 * <p>후보를 더해도 기존 생성 경로는 바뀌지 않는다. 합쳐진 목록이 그대로 {@link SelectedPlaceCatalog}로 들어가므로, 카탈로그 밖 장소를 만들어내지
 * 못하게 하는 검증이 후보에도 똑같이 걸린다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanCandidateFiller {

  private static final int RELAXED_PLACES_PER_DAY = 3;
  private static final int BALANCED_PLACES_PER_DAY = 4;
  private static final int TIGHT_PLACES_PER_DAY = 5;

  private final PlaceCandidateFinder placeCandidateFinder;

  /**
   * 고른 장소와 서버가 채운 후보.
   *
   * <p>어느 쪽에서 왔는지 호출자가 알아야 일정에 출처를 남길 수 있다. 순서는 고른 장소가 먼저다.
   */
  public record FilledPlaces(
      List<WizardPickedPlaceReqDto> places, Set<String> autoFilledProviderPlaceIds) {

    public boolean autoFilled(String providerPlaceId) {
      return autoFilledProviderPlaceIds.contains(providerPlaceId);
    }
  }

  /** 고른 장소 + 부족분 후보. 순서는 고른 장소가 먼저다. */
  public FilledPlaces fill(Travel travel, AiTravelPlanGenerateReqDto request) {
    List<WizardPickedPlaceReqDto> selected =
        request == null || request.selectedPlaces() == null
            ? List.of()
            : request.selectedPlaces().stream().filter(java.util.Objects::nonNull).toList();

    int needed = requiredPlaceCount(travel) - selected.size();
    if (needed < 1) {
      return new FilledPlaces(selected, Set.of());
    }

    Set<String> alreadyPicked =
        selected.stream()
            .map(WizardPickedPlaceReqDto::providerPlaceId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    List<WizardPickedPlaceReqDto> merged = new ArrayList<>(selected);
    Set<String> autoFilled = new LinkedHashSet<>();
    placeCandidateFinder.findCandidates(zonesOf(travel), alreadyPicked, needed).stream()
        .map(TravelPlanCandidateFiller::toWizardPlace)
        .forEach(
            place -> {
              merged.add(place);
              autoFilled.add(place.providerPlaceId());
            });
    return new FilledPlaces(merged, autoFilled);
  }

  /** 일수 × 하루 장소 수. 여행 속도가 빠를수록 더 많이 채운다. */
  private int requiredPlaceCount(Travel travel) {
    int days =
        Math.toIntExact(ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()) + 1);
    return Math.max(days, 0) * placesPerDay(travel.getPace());
  }

  private int placesPerDay(TravelPace pace) {
    if (pace == TravelPace.RELAXED) {
      return RELAXED_PLACES_PER_DAY;
    }
    if (pace == TravelPace.TIGHT) {
      return TIGHT_PLACES_PER_DAY;
    }
    return BALANCED_PLACES_PER_DAY;
  }

  /**
   * 후보를 고를 권역.
   *
   * <p>숙소 지역이 권역으로 해석되면 그 권역에서만 고르고, 아니면 부산 전체에서 고른다. 사용자가 고른 장소의 권역까지 따지지 않는 것은, 선택 장소에 권역 정보가 없어
   * 좌표로 역산해야 하기 때문이다. 필요해지면 그때 넓힌다.
   */
  private Set<ChatZone> zonesOf(Travel travel) {
    return AccommodationAreaZones.resolve(travel.getAccommodationArea())
        .map(Set::of)
        .orElseGet(Set::of);
  }

  private static WizardPickedPlaceReqDto toWizardPlace(PlaceCandidateResDto candidate) {
    return new WizardPickedPlaceReqDto(
        candidate.provider(),
        candidate.providerPlaceId(),
        candidate.name(),
        candidate.address(),
        candidate.latitude(),
        candidate.longitude(),
        candidate.contentTypeId());
  }
}
