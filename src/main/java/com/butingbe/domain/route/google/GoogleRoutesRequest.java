package com.butingbe.domain.route.google;

/** Google Routes {@code computeRoutes} 요청 본문. 출발·도착 좌표와 이동 수단만 담는다. */
public record GoogleRoutesRequest(
    Waypoint origin, Waypoint destination, String travelMode, String languageCode) {

  public record Waypoint(Location location) {}

  public record Location(LatLng latLng) {}

  public record LatLng(double latitude, double longitude) {}

  public static Waypoint waypoint(double latitude, double longitude) {
    return new Waypoint(new Location(new LatLng(latitude, longitude)));
  }
}
