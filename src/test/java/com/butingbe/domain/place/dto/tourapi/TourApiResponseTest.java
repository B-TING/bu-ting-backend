package com.butingbe.domain.place.dto.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TourApiResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("items가 빈 문자열이면 빈 목록으로 읽는다")
  void readsEmptyStringItemsAsEmptyList() throws Exception {
    TourApiResponse response =
        objectMapper.readValue(
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"numOfRows":10,"pageNo":1,"totalCount":0,"items":""}}}
            """,
            TourApiResponse.class);

    assertThat(response.response().body().items().item()).isEmpty();
  }

  @Test
  @DisplayName("items.item이 없거나 문자열이면 빈 목록으로 읽는다")
  void readsMissingOrStringItemAsEmptyList() throws Exception {
    TourApiResponse missing =
        objectMapper.readValue(
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"numOfRows":10,"pageNo":1,"totalCount":0,"items":{}}}}
            """,
            TourApiResponse.class);
    TourApiResponse stringItem =
        objectMapper.readValue(
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"numOfRows":10,"pageNo":1,"totalCount":0,"items":{"item":""}}}}
            """,
            TourApiResponse.class);

    assertThat(missing.response().body().items().item()).isEmpty();
    assertThat(stringItem.response().body().items().item()).isEmpty();
  }

  @Test
  @DisplayName("items.item이 객체 하나면 단일 항목 목록으로 읽는다")
  void readsSingleObjectItemAsSingletonList() throws Exception {
    TourApiResponse response =
        objectMapper.readValue(
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"numOfRows":10,"pageNo":1,"totalCount":1,
              "items":{"item":{"contentid":"12345","contenttypeid":"39","title":"국밥"}}}}}
            """,
            TourApiResponse.class);

    assertThat(response.response().body().items().item()).hasSize(1);
    assertThat(response.response().body().items().item().get(0).contentid()).isEqualTo("12345");
  }

  @Test
  @DisplayName("items.item이 배열이면 그대로 목록으로 읽는다")
  void readsArrayItemAsList() throws Exception {
    TourApiResponse response =
        objectMapper.readValue(
            """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"numOfRows":10,"pageNo":1,"totalCount":2,
              "items":{"item":[{"contentid":"1","title":"A"},{"contentid":"2","title":"B"}]}}}}
            """,
            TourApiResponse.class);

    assertThat(response.response().body().items().item()).hasSize(2);
  }
}
