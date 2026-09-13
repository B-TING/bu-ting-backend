package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.chat.entity.ChatZone;

/** 구역 식별자와 표시 이름. */
public record ZoneRef(String zoneId, String zoneName) {

  public static ZoneRef from(String zoneId) {
    return new ZoneRef(zoneId, ChatZone.valueOf(zoneId).getZoneName());
  }
}
