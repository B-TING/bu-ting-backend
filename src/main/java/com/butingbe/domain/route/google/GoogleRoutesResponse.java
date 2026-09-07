package com.butingbe.domain.route.google;

import java.util.List;

/**
 * Google Routes {@code computeRoutes} 응답의 필요한 부분만.
 *
 * <p>한국 안에서는 Google이 대중교통 외의 경로를 돌려주지 않아 {@code routes}가 비어 올 수 있다. 그 경우 호출자가 경로 없음으로 처리한다.
 */
public record GoogleRoutesResponse(List<Route> routes) {

  /** {@code duration}은 "921s"처럼 초 단위 문자열이다. */
  public record Route(Integer distanceMeters, String duration) {}
}
