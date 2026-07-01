package com.butingbe.domain.place.dto.tourapi;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Map;

public record TourApiDetailResponse(Response response) {

  public record Response(Header header, Body body) {}

  public record Header(String resultCode, String resultMsg) {}

  public record Body(Items items) {}

  public record Items(
      @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          List<Map<String, Object>> item) {}
}
