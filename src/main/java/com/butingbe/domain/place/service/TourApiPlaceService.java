package com.butingbe.domain.place.service;

import com.butingbe.domain.place.dto.googleplaces.GooglePlaceDetailsResponse;
import com.butingbe.domain.place.dto.googleplaces.GooglePlaceSearchRequest;
import com.butingbe.domain.place.dto.googleplaces.GooglePlaceSearchRequest.Circle;
import com.butingbe.domain.place.dto.googleplaces.GooglePlaceSearchRequest.Coordinate;
import com.butingbe.domain.place.dto.googleplaces.GooglePlaceSearchRequest.LocationBias;
import com.butingbe.domain.place.dto.googleplaces.GooglePlaceSearchResponse;
import com.butingbe.domain.place.dto.request.FestivalSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceLocationSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.FestivalResDto;
import com.butingbe.domain.place.dto.response.FestivalSearchResDto;
import com.butingbe.domain.place.dto.response.GooglePlaceInfoResDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import com.butingbe.domain.place.dto.tourapi.TourApiCommonResponse;
import com.butingbe.domain.place.dto.tourapi.TourApiCommonResponse.TourCommonItem;
import com.butingbe.domain.place.dto.tourapi.TourApiDetailResponse;
import com.butingbe.domain.place.dto.tourapi.TourApiResponse;
import com.butingbe.domain.place.dto.tourapi.TourPlaceItem;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@Slf4j
public class TourApiPlaceService implements PlaceService {

  private static final String DEFAULT_MOBILE_OS = "WEB";
  private static final String DEFAULT_MOBILE_APP = "buting";
  private static final String DEFAULT_RESPONSE_TYPE = "json";
  private static final String BUSAN_REGION_CODE = "26";
  private static final String DETAIL_MOBILE_OS = "AND";
  private static final String GOOGLE_LANGUAGE_CODE = "ko";
  private static final String GOOGLE_REGION_CODE = "KR";
  private static final int GOOGLE_SEARCH_MAX_RESULT_COUNT = 1;
  private static final double GOOGLE_LOCATION_BIAS_RADIUS_METERS = 500.0;
  private static final String GOOGLE_SEARCH_FIELD_MASK = "places.id";
  private static final String GOOGLE_DETAIL_FIELD_MASK =
      "id,rating,userRatingCount,priceLevel,regularOpeningHours.weekdayDescriptions,"
          + "reviews.rating,reviews.text,reviews.authorAttribution.displayName,"
          + "reviews.relativePublishTimeDescription,reviews.publishTime";

  private final RestClient restClient;
  private final String baseUrl;
  private final String serviceKey;
  private final String googlePlacesBaseUrl;
  private final String googlePlacesApiKey;

  @Autowired
  public TourApiPlaceService(
      @Value("${place.tour-api.base-url:https://apis.data.go.kr/B551011/KorService2}")
          String baseUrl,
      @Value("${place.tour-api.service-key:}") String serviceKey,
      @Value("${place.google-places.base-url:https://places.googleapis.com/v1/places}")
          String googlePlacesBaseUrl,
      @Value("${place.google-places.api-key:}") String googlePlacesApiKey) {
    this(RestClient.create(), baseUrl, serviceKey, googlePlacesBaseUrl, googlePlacesApiKey);
  }

  TourApiPlaceService(RestClient restClient, String baseUrl, String serviceKey) {
    this(restClient, baseUrl, serviceKey, "", "");
  }

  TourApiPlaceService(
      RestClient restClient,
      String baseUrl,
      String serviceKey,
      String googlePlacesBaseUrl,
      String googlePlacesApiKey) {
    this.restClient = restClient;
    this.baseUrl = baseUrl;
    this.serviceKey = serviceKey;
    this.googlePlacesBaseUrl = googlePlacesBaseUrl;
    this.googlePlacesApiKey = googlePlacesApiKey;
  }

  @Override
  public PlaceSearchResDto searchPlaces(PlaceSearchReqDto request) {
    if (!StringUtils.hasText(serviceKey)) {
      throw new IllegalStateException("Tour API service key is not configured.");
    }

    int page = request.pageOrDefault();
    int size = request.sizeOrDefault();
    TourApiResponse response =
        restClient
            .get()
            .uri(
                baseUrl + "/areaBasedList2",
                uriBuilder -> {
                  uriBuilder
                      .queryParam("numOfRows", size)
                      .queryParam("pageNo", page)
                      .queryParam("MobileOS", DEFAULT_MOBILE_OS)
                      .queryParam("MobileApp", DEFAULT_MOBILE_APP)
                      .queryParam("_type", DEFAULT_RESPONSE_TYPE)
                      .queryParam("arrange", request.arrangeOrDefault())
                      .queryParam("serviceKey", serviceKey)
                      .queryParam("lDongRegnCd", BUSAN_REGION_CODE);

                  if (StringUtils.hasText(request.districtCodeOrNull())) {
                    uriBuilder.queryParam("lDongSignguCd", request.districtCodeOrNull());
                  }
                  if (StringUtils.hasText(request.contentTypeIdOrNull())) {
                    uriBuilder.queryParam("contentTypeId", request.contentTypeIdOrNull());
                  }
                  return uriBuilder.build();
                })
            .retrieve()
            .body(TourApiResponse.class);

    TourApiResponse.Body body = body(response);
    List<PlaceResDto> places =
        items(body).stream().filter(Objects::nonNull).map(PlaceResDto::from).toList();
    return new PlaceSearchResDto(body.pageNo(), body.numOfRows(), body.totalCount(), places);
  }

  @Override
  public FestivalSearchResDto searchFestivals(FestivalSearchReqDto request) {
    if (!StringUtils.hasText(serviceKey)) {
      throw new IllegalStateException("Tour API service key is not configured.");
    }

    int page = request.pageOrDefault();
    int size = request.sizeOrDefault();

    TourApiResponse response =
        restClient
            .get()
            .uri(
                baseUrl + "/searchFestival2",
                uriBuilder -> {
                  uriBuilder
                      .queryParam("numOfRows", size)
                      .queryParam("pageNo", page)
                      .queryParam("MobileOS", DEFAULT_MOBILE_OS)
                      .queryParam("MobileApp", DEFAULT_MOBILE_APP)
                      .queryParam("_type", DEFAULT_RESPONSE_TYPE)
                      .queryParam("arrange", request.arrangeOrDefault())
                      .queryParam("eventStartDate", request.eventStartDate())
                      .queryParam("serviceKey", serviceKey)
                      .queryParam("lDongRegnCd", BUSAN_REGION_CODE);

                  if (StringUtils.hasText(request.eventEndDateOrNull())) {
                    uriBuilder.queryParam("eventEndDate", request.eventEndDateOrNull());
                  }
                  if (StringUtils.hasText(request.districtCodeOrNull())) {
                    uriBuilder.queryParam("lDongSignguCd", request.districtCodeOrNull());
                  }
                  return uriBuilder.build();
                })
            .retrieve()
            .body(TourApiResponse.class);

    TourApiResponse.Body body = body(response);
    List<FestivalResDto> festivals =
        items(body).stream().filter(Objects::nonNull).map(FestivalResDto::from).toList();
    return new FestivalSearchResDto(
        request.eventStartDate(),
        request.eventEndDateOrNull(),
        body.pageNo(),
        body.numOfRows(),
        body.totalCount(),
        festivals);
  }

  @Override
  public PlaceSearchResDto searchPlacesByLocation(PlaceLocationSearchReqDto request) {
    if (!StringUtils.hasText(serviceKey)) {
      throw new IllegalStateException("Tour API service key is not configured.");
    }

    int page = request.pageOrDefault();
    int size = request.sizeOrDefault();
    TourApiResponse response =
        restClient
            .get()
            .uri(
                baseUrl + "/locationBasedList2",
                uriBuilder -> {
                  uriBuilder
                      .queryParam("numOfRows", size)
                      .queryParam("pageNo", page)
                      .queryParam("MobileOS", DEFAULT_MOBILE_OS)
                      .queryParam("MobileApp", DEFAULT_MOBILE_APP)
                      .queryParam("_type", DEFAULT_RESPONSE_TYPE)
                      .queryParam("arrange", request.arrangeOrDefault())
                      .queryParam("mapX", request.mapX())
                      .queryParam("mapY", request.mapY())
                      .queryParam("radius", request.radius())
                      .queryParam("serviceKey", serviceKey);

                  if (StringUtils.hasText(request.contentTypeIdOrNull())) {
                    uriBuilder.queryParam("contentTypeId", request.contentTypeIdOrNull());
                  }
                  return uriBuilder.build();
                })
            .retrieve()
            .body(TourApiResponse.class);

    TourApiResponse.Body body = body(response);
    List<PlaceResDto> places =
        items(body).stream().filter(Objects::nonNull).map(PlaceResDto::from).toList();
    return new PlaceSearchResDto(body.pageNo(), body.numOfRows(), body.totalCount(), places);
  }

  @Override
  public PlaceDetailResDto getPlaceDetail(
      String contentId, String contentTypeId, String googleSearchText) {
    if (!StringUtils.hasText(serviceKey)) {
      throw new IllegalStateException("Tour API service key is not configured.");
    }
    if (!StringUtils.hasText(contentId) || !StringUtils.hasText(contentTypeId)) {
      throw new IllegalArgumentException("contentId and contentTypeId are required.");
    }

    TourApiDetailResponse response =
        restClient
            .get()
            .uri(
                baseUrl + "/detailIntro2",
                uriBuilder ->
                    uriBuilder
                        .queryParam("MobileOS", DETAIL_MOBILE_OS)
                        .queryParam("MobileApp", DEFAULT_MOBILE_APP)
                        .queryParam("_type", DEFAULT_RESPONSE_TYPE)
                        .queryParam("contentId", contentId)
                        .queryParam("contentTypeId", contentTypeId)
                        .queryParam("serviceKey", serviceKey)
                        .build())
            .retrieve()
            .body(TourApiDetailResponse.class);

    Map<String, Object> item = detailItems(response).stream().findFirst().orElse(Map.of());
    GooglePlaceInfoResDto googlePlace = googlePlaceInfo(contentId, googleSearchText);
    return PlaceDetailResDto.from(item, contentId, contentTypeId, googlePlace);
  }

  private TourApiResponse.Body body(TourApiResponse response) {
    if (response == null || response.response() == null || response.response().body() == null) {
      return new TourApiResponse.Body(new TourApiResponse.Items(List.of()), 0, 0, 0);
    }
    return response.response().body();
  }

  private List<TourPlaceItem> items(TourApiResponse.Body body) {
    if (body.items() == null || body.items().item() == null) {
      return List.of();
    }
    return body.items().item();
  }

  private List<Map<String, Object>> detailItems(TourApiDetailResponse response) {
    if (response == null
        || response.response() == null
        || response.response().body() == null
        || response.response().body().items() == null
        || response.response().body().items().item() == null) {
      return List.of();
    }
    return response.response().body().items().item();
  }

  private GooglePlaceInfoResDto googlePlaceInfo(String contentId, String googleSearchText) {
    if (!StringUtils.hasText(googlePlacesApiKey)) {
      log.debug("Google Places API key is not configured. contentId={}", contentId);
      return null;
    }

    try {
      if (StringUtils.hasText(googleSearchText)) {
        return googlePlaceInfoByText(contentId, googleSearchText.trim());
      }

      Optional<TourCommonItem> commonItem = tourCommonInfo(contentId);
      if (commonItem.isEmpty()) {
        log.warn("Tour API detailCommon2 returned no item. contentId={}", contentId);
        return null;
      }

      Optional<String> placeId = googlePlaceId(commonItem.get());
      if (placeId.isEmpty()) {
        log.warn(
            "Google Places search returned no place. contentId={}, query={}",
            contentId,
            googleSearchQuery(commonItem.get()));
        return null;
      }

      GooglePlaceDetailsResponse details = googlePlaceDetails(placeId.get());
      if (details == null) {
        log.warn(
            "Google Place Details returned empty response. contentId={}, placeId={}",
            contentId,
            placeId.get());
        return null;
      }
      return GooglePlaceInfoResDto.from(details);
    } catch (RestClientResponseException ex) {
      log.warn(
          "Failed to load Google Places info. contentId={}, status={}, body={}",
          contentId,
          ex.getStatusCode(),
          ex.getResponseBodyAsString(),
          ex);
      return null;
    } catch (RestClientException ex) {
      log.warn("Failed to load Google Places info. contentId={}", contentId, ex);
      return null;
    }
  }

  private GooglePlaceInfoResDto googlePlaceInfoByText(String contentId, String googleSearchText) {
    Optional<String> placeId = googlePlaceId(googleSearchText, null);
    if (placeId.isEmpty()) {
      log.warn(
          "Google Places search returned no place. contentId={}, query={}",
          contentId,
          googleSearchText);
      return null;
    }

    GooglePlaceDetailsResponse details = googlePlaceDetails(placeId.get());
    if (details == null) {
      log.warn(
          "Google Place Details returned empty response. contentId={}, placeId={}",
          contentId,
          placeId.get());
      return null;
    }
    return GooglePlaceInfoResDto.from(details);
  }

  private Optional<TourCommonItem> tourCommonInfo(String contentId) {
    TourApiCommonResponse response =
        restClient
            .get()
            .uri(
                baseUrl + "/detailCommon2",
                uriBuilder ->
                    uriBuilder
                        .queryParam("MobileOS", DETAIL_MOBILE_OS)
                        .queryParam("MobileApp", DEFAULT_MOBILE_APP)
                        .queryParam("_type", DEFAULT_RESPONSE_TYPE)
                        .queryParam("contentId", contentId)
                        .queryParam("defaultYN", "Y")
                        .queryParam("addrinfoYN", "Y")
                        .queryParam("mapinfoYN", "Y")
                        .queryParam("serviceKey", serviceKey)
                        .build())
            .retrieve()
            .body(TourApiCommonResponse.class);

    return commonItems(response).stream().findFirst();
  }

  private List<TourCommonItem> commonItems(TourApiCommonResponse response) {
    if (response == null
        || response.response() == null
        || response.response().body() == null
        || response.response().body().items() == null
        || response.response().body().items().item() == null) {
      return List.of();
    }
    return response.response().body().items().item();
  }

  private String googleSearchQuery(TourCommonItem item) {
    return Stream.of(item.title(), item.addr1())
        .filter(StringUtils::hasText)
        .map(String::trim)
        .reduce((left, right) -> left + " " + right)
        .orElse("");
  }

  private Optional<String> googlePlaceId(TourCommonItem item) {
    String textQuery = googleSearchQuery(item);
    if (!StringUtils.hasText(textQuery)) {
      log.debug("Google Places search query is empty. contentId={}", item.contentid());
      return Optional.empty();
    }
    return googlePlaceId(textQuery, locationBias(item));
  }

  private Optional<String> googlePlaceId(String textQuery, LocationBias locationBias) {
    GooglePlaceSearchResponse response =
        restClient
            .post()
            .uri(googlePlacesBaseUrl + ":searchText")
            .header("X-Goog-Api-Key", googlePlacesApiKey)
            .header("X-Goog-FieldMask", GOOGLE_SEARCH_FIELD_MASK)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new GooglePlaceSearchRequest(
                    textQuery,
                    GOOGLE_LANGUAGE_CODE,
                    GOOGLE_REGION_CODE,
                    GOOGLE_SEARCH_MAX_RESULT_COUNT,
                    locationBias))
            .retrieve()
            .body(GooglePlaceSearchResponse.class);

    if (response == null || response.places() == null) {
      log.warn("Google Places search returned empty response. query={}", textQuery);
      return Optional.empty();
    }
    return response.places().stream()
        .map(GooglePlaceSearchResponse.Place::id)
        .filter(StringUtils::hasText)
        .findFirst();
  }

  private GooglePlaceDetailsResponse googlePlaceDetails(String placeId) {
    return restClient
        .get()
        .uri(
            googlePlacesBaseUrl + "/" + placeId,
            uriBuilder ->
                uriBuilder
                    .queryParam("languageCode", GOOGLE_LANGUAGE_CODE)
                    .queryParam("regionCode", GOOGLE_REGION_CODE)
                    .build())
        .header("X-Goog-Api-Key", googlePlacesApiKey)
        .header("X-Goog-FieldMask", GOOGLE_DETAIL_FIELD_MASK)
        .retrieve()
        .body(GooglePlaceDetailsResponse.class);
  }

  private LocationBias locationBias(TourCommonItem item) {
    Double longitude = parseDouble(item.mapx());
    Double latitude = parseDouble(item.mapy());
    if (longitude == null || latitude == null) {
      return null;
    }
    return new LocationBias(
        new Circle(new Coordinate(latitude, longitude), GOOGLE_LOCATION_BIAS_RADIUS_METERS));
  }

  private Double parseDouble(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
