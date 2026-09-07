package com.butingbe.domain.zoneevent.exception;

import lombok.Getter;

/** 참여·제출 시 GPS가 타겟 반경 밖일 때. 400으로 매핑하며 거리(m)를 함께 돌려준다. */
@Getter
public class ZoneEventOutOfRangeException extends RuntimeException {

  private final int distanceMeters;

  public ZoneEventOutOfRangeException(int distanceMeters) {
    super("error.zone_event.out_of_range");
    this.distanceMeters = distanceMeters;
  }
}
