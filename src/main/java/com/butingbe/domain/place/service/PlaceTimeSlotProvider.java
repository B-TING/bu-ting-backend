package com.butingbe.domain.place.service;

import com.butingbe.domain.place.entity.PlaceTimeSlot;
import java.util.Optional;

/**
 * 장소가 어울리는 시간대를 알려준다.
 *
 * <p>야경이 핵심인 장소를 오전에 두면 일정이 무의미해진다. 이 판단은 데이터로 유도할 수 없어 카탈로그에 사람이 넣어 둔 값을 읽는다. 호출자가 장소 도메인의 저장소를 직접
 * 다루지 않도록 조회 경로를 좁힌다.
 */
public interface PlaceTimeSlotProvider {

  /** 지정된 시간대가 없으면 비어 있다. 대다수 장소가 여기 해당한다. */
  Optional<PlaceTimeSlot> timeSlot(String provider, String providerPlaceId);
}
