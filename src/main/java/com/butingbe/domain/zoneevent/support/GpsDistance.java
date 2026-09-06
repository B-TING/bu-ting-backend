package com.butingbe.domain.zoneevent.support;

/**
 * 두 GPS 좌표 사이의 대권 거리(Haversine).
 *
 * <p>{@code route.HaversineRouteProvider}와 같은 공식이지만, 두 도메인이 실제로 공유하는지 확정되기 전까지 route 엔티티에 결합하지 않도록
 * 여기서 동일 공식을 둔다(명세 구현 메모). 지구 반경 6,371,000m, 결과는 정수 m로 내림한다(BR-03).
 */
public final class GpsDistance {

  private static final double EARTH_RADIUS_METERS = 6_371_000;

  private GpsDistance() {}

  public static int meters(double fromLat, double fromLng, double toLat, double toLng) {
    double latitudeDelta = Math.toRadians(toLat - fromLat);
    double longitudeDelta = Math.toRadians(toLng - fromLng);
    double haversine =
        Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(Math.toRadians(fromLat))
                * Math.cos(Math.toRadians(toLat))
                * Math.sin(longitudeDelta / 2)
                * Math.sin(longitudeDelta / 2);
    double distance = EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(Math.min(1, haversine)));
    return (int) Math.floor(distance);
  }
}
