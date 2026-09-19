package com.butingbe.domain.place.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.PlaceResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import com.butingbe.domain.place.dto.response.PlaceSyncResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.entity.PlaceDwellDefaults;
import com.butingbe.domain.place.entity.PlaceZoneResolver;
import com.butingbe.domain.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TourAPI 부산 장소를 {@code place} 테이블로 적재한다.
 *
 * <p>TourAPI 호출은 {@link PlaceService}를 그대로 쓴다. 조회 경로가 이미 부산 지역 코드와 페이징을 다루므로 여기서 별도의 클라이언트를 만들지
 * 않는다.
 *
 * <p>{@code contentId} 기준 upsert라 몇 번을 돌려도 행이 늘지 않는다. 권역은 적재 시점에 {@code lDongSignguCd}로 채운다.
 *
 * <p>자동 스케줄은 두지 않는다. 관광 데이터는 자주 바뀌지 않고, 운영이 원하는 시점에 부르는 편이 예측 가능하다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlaceSyncService {

  static final String PROVIDER = "TOUR_API";
  private static final int PAGE_SIZE = 100;
  private static final int MAX_PAGES = 200;

  private final PlaceService placeService;
  private final PlaceRepository placeRepository;
  private final OperatorAuthorization operatorAuthorization;

  /** 부산 전체를 페이지 끝까지 훑어 적재한다. 운영자만 호출할 수 있다. */
  @Transactional
  public PlaceSyncResDto sync(AuthenticatedUser user) {
    operatorAuthorization.requireOperator(user);

    int created = 0;
    int updated = 0;
    int skipped = 0;
    int failedPages = 0;
    int totalCount = 0;

    for (int page = 1; page <= MAX_PAGES; page++) {
      PlaceSearchResDto response;
      try {
        response =
            placeService.searchPlaces(new PlaceSearchReqDto(page, PAGE_SIZE, null, null, null));
      } catch (RuntimeException e) {
        // 한 페이지가 실패해도 적재 전체를 버리지 않는다. 다음 동기화에서 다시 채운다.
        log.warn("Place sync failed on page {}. reason={}", page, e.toString());
        failedPages++;
        continue;
      }

      totalCount = response.totalCount();
      if (response.places().isEmpty()) {
        break;
      }

      for (PlaceResDto item : response.places()) {
        if (item.contentId() == null || item.contentId().isBlank() || item.title() == null) {
          skipped++;
          continue;
        }
        if (upsert(item)) {
          created++;
        } else {
          updated++;
        }
      }

      if (page * PAGE_SIZE >= totalCount) {
        break;
      }
    }

    log.info(
        "Place sync finished. created={} updated={} skipped={} failedPages={}",
        created,
        updated,
        skipped,
        failedPages);
    return new PlaceSyncResDto(created, updated, skipped, failedPages, totalCount);
  }

  /** 새로 저장했으면 true, 기존 행을 갱신했으면 false. */
  private boolean upsert(PlaceResDto item) {
    ChatZone zone = PlaceZoneResolver.resolve(item.districtCode()).orElse(null);
    return placeRepository
        .findByProviderAndProviderPlaceId(PROVIDER, item.contentId())
        .map(
            existing -> {
              // 체류 시간이 비어 있으면 이번 동기화에서 채운다. 이미 값이 있으면 건드리지 않는다.
              existing.fillDwellMinutesIfAbsent(
                  PlaceDwellDefaults.forContentType(item.contentTypeId()));
              existing.applySync(
                  item.title(),
                  item.address(),
                  item.latitude(),
                  item.longitude(),
                  item.contentTypeId(),
                  item.imageUrl(),
                  zone,
                  item.districtCode());
              return false;
            })
        .orElseGet(
            () -> {
              placeRepository.save(
                  Place.builder()
                      .provider(PROVIDER)
                      .providerPlaceId(item.contentId())
                      .name(item.title())
                      .address(item.address())
                      .latitude(item.latitude())
                      .longitude(item.longitude())
                      .contentTypeId(item.contentTypeId())
                      .imageUrl(item.imageUrl())
                      .zoneId(zone)
                      .districtCode(item.districtCode())
                      .dwellMinutes(PlaceDwellDefaults.forContentType(item.contentTypeId()))
                      .build());
              return true;
            });
  }
}
