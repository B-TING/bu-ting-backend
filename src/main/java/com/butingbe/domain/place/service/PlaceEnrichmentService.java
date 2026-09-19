package com.butingbe.domain.place.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.place.dto.response.GooglePlaceInfoResDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceEnrichResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소의 인기도(평점·리뷰 수)를 Google Places에서 받아 채운다.
 *
 * <p>TourAPI는 평점도 리뷰 수도 주지 않는다. "사람들이 많이 가는 곳 우선" 추천의 근거가 되는 신호라 따로 보강해야 한다.
 *
 * <p>장소마다 외부 호출이 들어가므로 한 번에 처리할 수를 제한하고, 아직 보강하지 않은 장소부터 고른다. 평점이 없는 장소도 보강 시각을 남겨 같은 장소를 계속 다시
 * 조회하지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlaceEnrichmentService {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 300;

  private final PlaceService placeService;
  private final PlaceRepository placeRepository;
  private final OperatorAuthorization operatorAuthorization;

  /** 보강이 필요한 장소를 limit 건만큼 처리한다. 운영자만 호출할 수 있다. */
  @Transactional
  public PlaceEnrichResDto enrich(AuthenticatedUser user, Integer limit) {
    operatorAuthorization.requireOperator(user);

    int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
    List<Place> targets = placeRepository.findEnrichmentTargets(PageRequest.of(0, size));

    int enriched = 0;
    int withoutRating = 0;
    int failed = 0;
    LocalDateTime now = LocalDateTime.now();

    for (Place place : targets) {
      try {
        GooglePlaceInfoResDto info = googleInfo(place);
        if (info == null || info.rating() == null) {
          // 구글에 없는 장소도 시각을 남긴다. 남기지 않으면 매번 같은 장소를 다시 조회한다.
          place.applyPopularity(null, null, now);
          withoutRating++;
          continue;
        }
        place.applyPopularity(BigDecimal.valueOf(info.rating()), info.reviewCount(), now);
        enriched++;
      } catch (RuntimeException e) {
        // 한 장소의 실패가 나머지 보강을 막지 않는다. 시각을 남기지 않으므로 다음 호출에서 다시 시도된다.
        log.warn(
            "Place enrichment failed for {}. reason={}", place.getProviderPlaceId(), e.toString());
        failed++;
      }
    }

    log.info(
        "Place enrichment finished. enriched={} withoutRating={} failed={}",
        enriched,
        withoutRating,
        failed);
    return new PlaceEnrichResDto(targets.size(), enriched, withoutRating, failed);
  }

  private GooglePlaceInfoResDto googleInfo(Place place) {
    PlaceDetailResDto detail =
        placeService.getPlaceDetail(
            place.getProviderPlaceId(), place.getContentTypeId(), place.getName());
    return detail == null ? null : detail.googlePlace();
  }
}
