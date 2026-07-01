package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.butingbe.domain.place.dto.request.PlaceLocationSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TourApiPlaceServiceTest {

  @Test
  @DisplayName("공공데이터 지역기반 관광정보를 필요한 Place DTO 필드로 변환한다")
  void searchPlacesMapsTourApiItemsToPlaceDtos() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(builder.build(), "https://tour.example.com", "SERVICE_KEY");

    server
        .expect(
            requestTo(
                "https://tour.example.com/areaBasedList2"
                    + "?numOfRows=10"
                    + "&pageNo=2"
                    + "&MobileOS=WEB"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&arrange=C"
                    + "&serviceKey=SERVICE_KEY"
                    + "&lDongRegnCd=26"
                    + "&contentTypeId=39"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {
                      "resultCode": "0000",
                      "resultMsg": "OK"
                    },
                    "body": {
                      "numOfRows": 10,
                      "pageNo": 2,
                      "totalCount": 1,
                      "items": {
                        "item": [
                          {
                            "addr1": "부산광역시 강서구 가락대로 1206 (봉림동)",
                            "addr2": "",
                            "contentid": "2869277",
                            "contenttypeid": "39",
                            "firstimage": "https://tong.visitkorea.or.kr/cms/resource/70/2869270_image2_1.jpg",
                            "firstimage2": "https://tong.visitkorea.or.kr/cms/resource/70/2869270_image3_1.jpg",
                            "mapx": "128.9010323937",
                            "mapy": "35.1724954738",
                            "tel": "",
                            "title": "오플로우",
                            "zipcode": "46709",
                            "lDongRegnCd": "26",
                            "lDongSignguCd": "440"
                          }
                        ]
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    PlaceSearchResDto response =
        placeService.searchPlaces(new PlaceSearchReqDto(2, 10, null, "39", "C"));

    assertThat(response.page()).isEqualTo(2);
    assertThat(response.size()).isEqualTo(10);
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.places()).hasSize(1);
    assertThat(response.places().getFirst().contentId()).isEqualTo("2869277");
    assertThat(response.places().getFirst().title()).isEqualTo("오플로우");
    assertThat(response.places().getFirst().address()).contains("부산광역시 강서구");
    assertThat(response.places().getFirst().longitude()).isEqualTo(128.9010323937);
    assertThat(response.places().getFirst().latitude()).isEqualTo(35.1724954738);
    assertThat(response.places().getFirst().regionCode()).isEqualTo("26");
    assertThat(response.places().getFirst().districtCode()).isEqualTo("440");
    server.verify();
  }

  @Test
  @DisplayName("공공데이터 위치기반 관광정보를 필요한 Place DTO 필드로 변환한다")
  void searchPlacesByLocationMapsTourApiItemsToPlaceDtos() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(builder.build(), "https://tour.example.com", "SERVICE_KEY");

    server
        .expect(
            requestTo(
                "https://tour.example.com/locationBasedList2"
                    + "?numOfRows=5"
                    + "&pageNo=1"
                    + "&MobileOS=WEB"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&arrange=E"
                    + "&mapX=129.16"
                    + "&mapY=35.163"
                    + "&radius=1000"
                    + "&serviceKey=SERVICE_KEY"
                    + "&contentTypeId=32"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {
                      "resultCode": "0000",
                      "resultMsg": "OK"
                    },
                    "body": {
                      "numOfRows": 5,
                      "pageNo": 1,
                      "totalCount": 1,
                      "items": {
                        "item": [
                          {
                            "addr1": "부산광역시 해운대구",
                            "contentid": "2651318",
                            "contenttypeid": "32",
                            "firstimage": "https://example.com/image.jpg",
                            "firstimage2": "https://example.com/thumb.jpg",
                            "mapx": "129.160",
                            "mapy": "35.163",
                            "title": "부산 호텔",
                            "lDongRegnCd": "26",
                            "lDongSignguCd": "350"
                          }
                        ]
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    PlaceSearchResDto response =
        placeService.searchPlacesByLocation(
            new PlaceLocationSearchReqDto(1, 5, 129.160, 35.163, 1000, "32", "E"));

    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(5);
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.places()).hasSize(1);
    assertThat(response.places().getFirst().contentId()).isEqualTo("2651318");
    assertThat(response.places().getFirst().title()).isEqualTo("부산 호텔");
    assertThat(response.places().getFirst().longitude()).isEqualTo(129.160);
    assertThat(response.places().getFirst().latitude()).isEqualTo(35.163);
    server.verify();
  }

  @Test
  @DisplayName("공공데이터 상세소개 정보를 content type별 상세 필드로 변환한다")
  void getPlaceDetailMapsTourApiDetailToPlaceDetailDto() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(builder.build(), "https://tour.example.com", "SERVICE_KEY");

    server
        .expect(
            requestTo(
                "https://tour.example.com/detailIntro2"
                    + "?MobileOS=AND"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&contentId=2651318"
                    + "&contentTypeId=32"
                    + "&serviceKey=SERVICE_KEY"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {
                      "resultCode": "0000",
                      "resultMsg": "OK"
                    },
                    "body": {
                      "items": {
                        "item": {
                          "contentid": "2651318",
                          "contenttypeid": "32",
                          "checkintime": "15:00",
                          "checkouttime": "11:00",
                          "parkinglodging": "주차 가능",
                          "reservationurl": "https://example.com",
                          "emptyfield": ""
                        }
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    PlaceDetailResDto response = placeService.getPlaceDetail("2651318", "32", null);

    assertThat(response.contentId()).isEqualTo("2651318");
    assertThat(response.contentTypeId()).isEqualTo("32");
    assertThat(response.details())
        .containsEntry("checkintime", "15:00")
        .containsEntry("checkouttime", "11:00")
        .containsEntry("parkinglodging", "주차 가능")
        .containsEntry("reservationurl", "https://example.com")
        .doesNotContainKeys("contentid", "contenttypeid", "emptyfield");
    assertThat(response.googlePlace()).isNull();
    server.verify();
  }

  @Test
  @DisplayName("장소 상세 정보에 Google Places 평점, 리뷰, 가격대, 운영시간을 합친다")
  void getPlaceDetailMergesGooglePlaceDetails() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(
            builder.build(),
            "https://tour.example.com",
            "TOUR_KEY",
            "https://places.example.com/v1/places",
            "GOOGLE_KEY");

    server
        .expect(
            requestTo(
                "https://tour.example.com/detailIntro2"
                    + "?MobileOS=AND"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&contentId=2651318"
                    + "&contentTypeId=32"
                    + "&serviceKey=TOUR_KEY"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": {
                          "contentid": "2651318",
                          "contenttypeid": "32",
                          "checkintime": "15:00"
                        }
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(
            requestTo(
                "https://tour.example.com/detailCommon2"
                    + "?MobileOS=AND"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&contentId=2651318"
                    + "&defaultYN=Y"
                    + "&addrinfoYN=Y"
                    + "&mapinfoYN=Y"
                    + "&serviceKey=TOUR_KEY"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": {
                          "contentid": "2651318",
                          "title": "부산 호텔",
                          "addr1": "부산광역시 해운대구",
                          "mapx": "129.160",
                          "mapy": "35.163"
                        }
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://places.example.com/v1/places:searchText"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Goog-Api-Key", "GOOGLE_KEY"))
        .andExpect(header("X-Goog-FieldMask", "places.id"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "textQuery": "부산 호텔 부산광역시 해운대구",
                      "languageCode": "ko",
                      "regionCode": "KR",
                      "maxResultCount": 1,
                      "locationBias": {
                        "circle": {
                          "center": {
                            "latitude": 35.163,
                            "longitude": 129.160
                          },
                          "radius": 500.0
                        }
                      }
                    }
                    """))
        .andRespond(
            withSuccess(
                """
                {
                  "places": [
                    {
                      "id": "google-place-id"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(
            requestTo(
                "https://places.example.com/v1/places/google-place-id"
                    + "?languageCode=ko"
                    + "&regionCode=KR"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-Goog-Api-Key", "GOOGLE_KEY"))
        .andRespond(
            withSuccess(
                """
                {
                  "id": "google-place-id",
                  "rating": 4.4,
                  "userRatingCount": 128,
                  "priceLevel": "PRICE_LEVEL_MODERATE",
                  "regularOpeningHours": {
                    "weekdayDescriptions": [
                      "월요일: 오전 9:00 ~ 오후 6:00",
                      "화요일: 오전 9:00 ~ 오후 6:00"
                    ]
                  },
                  "reviews": [
                    {
                      "rating": 5,
                      "text": {
                        "text": "좋았어요",
                        "languageCode": "ko"
                      },
                      "authorAttribution": {
                        "displayName": "리뷰어"
                      },
                      "relativePublishTimeDescription": "1개월 전",
                      "publishTime": "2026-06-01T00:00:00Z"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    PlaceDetailResDto response = placeService.getPlaceDetail("2651318", "32", null);

    assertThat(response.details()).containsEntry("checkintime", "15:00");
    assertThat(response.googlePlace()).isNotNull();
    assertThat(response.googlePlace().placeId()).isEqualTo("google-place-id");
    assertThat(response.googlePlace().rating()).isEqualTo(4.4);
    assertThat(response.googlePlace().reviewCount()).isEqualTo(128);
    assertThat(response.googlePlace().priceLevel()).isEqualTo("PRICE_LEVEL_MODERATE");
    assertThat(response.googlePlace().openingHours()).hasSize(2);
    assertThat(response.googlePlace().reviews()).hasSize(1);
    assertThat(response.googlePlace().reviews().getFirst().text()).isEqualTo("좋았어요");
    server.verify();
  }

  @Test
  @DisplayName("Google Places 검색 텍스트가 있으면 detailCommon2 없이 장소 보강 정보를 조회한다")
  void getPlaceDetailUsesGoogleSearchTextBeforeTourCommonInfo() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceService placeService =
        new TourApiPlaceService(
            builder.build(),
            "https://tour.example.com",
            "TOUR_KEY",
            "https://places.example.com/v1/places",
            "GOOGLE_KEY");

    server
        .expect(
            requestTo(
                "https://tour.example.com/detailIntro2"
                    + "?MobileOS=AND"
                    + "&MobileApp=buting"
                    + "&_type=json"
                    + "&contentId=2651318"
                    + "&contentTypeId=32"
                    + "&serviceKey=TOUR_KEY"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "body": {
                      "items": {
                        "item": {
                          "contentid": "2651318",
                          "contenttypeid": "32"
                        }
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://places.example.com/v1/places:searchText"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Goog-Api-Key", "GOOGLE_KEY"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "textQuery": "파라다이스 호텔 부산",
                      "languageCode": "ko",
                      "regionCode": "KR",
                      "maxResultCount": 1
                    }
                    """))
        .andRespond(
            withSuccess(
                """
                {
                  "places": [
                    {
                      "id": "google-place-id"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(
            requestTo(
                "https://places.example.com/v1/places/google-place-id"
                    + "?languageCode=ko"
                    + "&regionCode=KR"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {
                  "id": "google-place-id",
                  "rating": 4.7,
                  "userRatingCount": 300
                }
                """,
                MediaType.APPLICATION_JSON));

    PlaceDetailResDto response = placeService.getPlaceDetail("2651318", "32", "파라다이스 호텔 부산");

    assertThat(response.googlePlace()).isNotNull();
    assertThat(response.googlePlace().placeId()).isEqualTo("google-place-id");
    assertThat(response.googlePlace().rating()).isEqualTo(4.7);
    assertThat(response.googlePlace().reviewCount()).isEqualTo(300);
    server.verify();
  }
}
