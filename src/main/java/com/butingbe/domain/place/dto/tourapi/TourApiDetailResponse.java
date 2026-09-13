package com.butingbe.domain.place.dto.tourapi;

import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record TourApiDetailResponse(Response response) {

  public record Response(Header header, Body body) {}

  public record Header(String resultCode, String resultMsg) {}

  public record Body(Items items) {}

  @JsonDeserialize(using = ItemsDeserializer.class)
  public record Items(List<Map<String, Object>> item) {}

  public static class ItemsDeserializer extends ValueDeserializer<Items> {

    @Override
    public Items deserialize(JsonParser parser, DeserializationContext context)
        throws JacksonException {
      JsonNode itemsNode = context.readTree(parser);
      if (itemsNode == null || itemsNode.isNull() || itemsNode.isString()) {
        return new Items(List.of());
      }

      JsonNode itemNode = itemsNode.path("item");
      if (itemNode.isMissingNode() || itemNode.isNull() || itemNode.isString()) {
        return new Items(List.of());
      }

      JavaType mapType =
          context.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
      if (itemNode.isArray()) {
        JavaType itemListType =
            context.getTypeFactory().constructCollectionType(List.class, mapType);
        List<Map<String, Object>> items = context.readTreeAsValue(itemNode, itemListType);
        return new Items(items);
      }
      Map<String, Object> item = context.readTreeAsValue(itemNode, mapType);
      return new Items(List.of(item));
    }
  }
}
