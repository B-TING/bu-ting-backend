package com.butingbe.domain.place.dto.tourapi;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public record TourApiCommonResponse(Response response) {

  public record Response(Header header, Body body) {}

  public record Header(String resultCode, String resultMsg) {}

  public record Body(Items items) {}

  public record Items(
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          List<TourCommonItem> item) {}

  public record TourCommonItem(
      String contentid, String title, String addr1, String mapx, String mapy) {}
}
