package com.butingbe.domain.place.service;

/**
 * 장소의 체류 시간(분)을 알려준다.
 *
 * <p>일정 배치는 이동 시간만으로 하루 분량을 정할 수 없다. 머무는 시간을 알아야 하는데 이 값은 카탈로그에만 있다. 호출자가 장소 도메인의 저장소를 직접 들여다보지 않도록
 * 조회 경로를 이 인터페이스 하나로 좁힌다.
 */
public interface PlaceDwellTimeProvider {

  /** 카탈로그에 값이 없으면 유형 기본값으로 답한다. 0이나 음수는 돌려주지 않는다. */
  int dwellMinutes(String provider, String providerPlaceId);
}
