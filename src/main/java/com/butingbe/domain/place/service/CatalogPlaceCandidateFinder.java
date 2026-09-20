package com.butingbe.domain.place.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.response.PlaceCandidateResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적재된 카탈로그에서 인기순으로 후보를 고른다.
 *
 * <p>인기도는 평점과 리뷰 수를 함께 본다. 평점만 보면 리뷰 3개짜리 5.0이 위로 오고, 리뷰 수만 보면 평이 나쁜 유명 장소가 올라온다. 점수는 저장하지 않고 고를 때
 * 계산한다 — 가중치를 바꿔도 적재분을 다시 손댈 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class CatalogPlaceCandidateFinder implements PlaceCandidateFinder {

  /** 인기순 재정렬을 위해 넉넉히 가져올 배수. */
  private static final int OVERFETCH = 5;

  private final PlaceRepository placeRepository;

  @Override
  @Transactional(readOnly = true)
  public List<PlaceCandidateResDto> findCandidates(
      Collection<ChatZone> zones, Collection<String> excludedProviderPlaceIds, int limit) {
    if (limit < 1) {
      return List.of();
    }
    Collection<ChatZone> zoneFilter = zones == null || zones.isEmpty() ? null : zones;
    Set<String> excluded =
        excludedProviderPlaceIds == null ? Set.of() : Set.copyOf(excludedProviderPlaceIds);

    return placeRepository
        .findCandidates(zoneFilter, PageRequest.of(0, limit * OVERFETCH + excluded.size()))
        .stream()
        .filter(place -> !excluded.contains(place.getProviderPlaceId()))
        .sorted(Comparator.comparingDouble(CatalogPlaceCandidateFinder::popularity).reversed())
        .limit(limit)
        .map(PlaceCandidateResDto::from)
        .toList();
  }

  /** 평점 × log(리뷰 수). 리뷰가 없으면 0에 가깝게 떨어진다. */
  private static double popularity(Place place) {
    double rating = place.getRating() == null ? 0 : place.getRating().doubleValue();
    int reviews = place.getReviewCount() == null ? 0 : place.getReviewCount();
    return rating * Math.log(reviews + 1.0);
  }
}
