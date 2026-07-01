package com.butingbe.domain.place.dto.response;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

public record PlaceDetailResDto(
    String contentId,
    String contentTypeId,
    Map<String, String> details,
    GooglePlaceInfoResDto googlePlace) {

  private static final String CONTENT_ID_KEY = "contentid";
  private static final String CONTENT_TYPE_ID_KEY = "contenttypeid";

  public static PlaceDetailResDto from(
      Map<String, Object> item, String fallbackContentId, String fallbackContentTypeId) {
    return from(item, fallbackContentId, fallbackContentTypeId, null);
  }

  public static PlaceDetailResDto from(
      Map<String, Object> item,
      String fallbackContentId,
      String fallbackContentTypeId,
      GooglePlaceInfoResDto googlePlace) {
    Map<String, String> details = new LinkedHashMap<>();
    if (item != null) {
      item.forEach(
          (key, value) -> {
            String text = value == null ? null : String.valueOf(value).trim();
            if (StringUtils.hasText(key)
                && StringUtils.hasText(text)
                && !CONTENT_ID_KEY.equalsIgnoreCase(key)
                && !CONTENT_TYPE_ID_KEY.equalsIgnoreCase(key)) {
              details.put(key, text);
            }
          });
    }

    return new PlaceDetailResDto(
        valueOrFallback(item, CONTENT_ID_KEY, fallbackContentId),
        valueOrFallback(item, CONTENT_TYPE_ID_KEY, fallbackContentTypeId),
        details,
        googlePlace);
  }

  private static String valueOrFallback(Map<String, Object> item, String key, String fallback) {
    Object value = item == null ? null : item.get(key);
    String text = value == null ? null : String.valueOf(value).trim();
    return StringUtils.hasText(text) ? text : fallback;
  }
}
