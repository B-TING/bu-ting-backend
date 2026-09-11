package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import java.time.LocalDateTime;

public record AdminZoneTitleDefResDto(
    String titleDefId,
    String titleCode,
    String zoneId,
    Integer tier,
    Integer requiredSuccessCount,
    String titleName,
    String style,
    String color,
    long holderCount,
    Long revision,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminZoneTitleDefResDto of(ZoneTitleDef def, long holderCount) {
    return new AdminZoneTitleDefResDto(
        def.getId().toString(),
        def.getTitleCode(),
        def.getZoneId(),
        def.getTier(),
        def.getRequiredSuccessCount(),
        def.getTitleName(),
        def.getStyle(),
        def.getColor(),
        holderCount,
        def.getRevision(),
        def.getCreatedAt(),
        def.getUpdatedAt());
  }
}
