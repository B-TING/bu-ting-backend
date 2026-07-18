package com.butingbe.domain.storage.dto;

import com.butingbe.domain.storage.entity.StorageLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record StorageLocationResDto(
    String id,
    int line,
    String stationName,
    String locationDetail,
    double latitude,
    double longitude,
    int distanceMeters,
    boolean openNow,
    Counts counts,
    String cost,
    String company,
    List<Fee> fees) {

  public record Counts(int small, int medium, int large, int extraLarge) {}

  public static StorageLocationResDto from(
      Locker locker, Coordinate coordinate, int distanceMeters) {
    return new StorageLocationResDto(
        locker.id(),
        locker.line(),
        locker.name(),
        locker.locationDetail(),
        coordinate.latitude(),
        coordinate.longitude(),
        distanceMeters,
        true,
        new Counts(
            locker.counts().small(),
            locker.counts().medium(),
            locker.counts().large(),
            locker.counts().extraLarge()),
        locker.costRaw(),
        locker.company(),
        locker.fees());
  }

  public record Locker(
      String id,
      int line,
      String name,
      String locationDetail,
      Counts counts,
      String costRaw,
      List<Fee> fees,
      String company) {}

  public record Fee(String schedule, List<FeeItem> items) {}

  public record FeeItem(String size, int amount, String unit) {}

  public record Coordinate(double latitude, double longitude) {}

  public static StorageLocationResDto from(StorageLocation location, int distanceMeters) {
    return new StorageLocationResDto(
        location.getId().toString(),
        location.getLine(),
        location.getStationName(),
        location.getLocationDetail(),
        location.getLatitude(),
        location.getLongitude(),
        distanceMeters,
        true,
        new Counts(
            location.getSmallCount(),
            location.getMediumCount(),
            location.getLargeCount(),
            location.getExtraLargeCount()),
        location.getCostRaw(),
        location.getCompany(),
        groupFees(location));
  }

  private static List<Fee> groupFees(StorageLocation location) {
    if (location.getFees() == null) {
      return List.of();
    }
    Map<String, List<FeeItem>> grouped =
        location.getFees().stream()
            .collect(
                Collectors.groupingBy(
                    fee -> fee.getScheduleType(),
                    LinkedHashMap::new,
                    Collectors.mapping(
                        fee ->
                            new FeeItem(fee.getLockerSize(), fee.getAmount(), fee.getBillingUnit()),
                        Collectors.toList())));
    return grouped.entrySet().stream()
        .map(entry -> new Fee(entry.getKey(), entry.getValue()))
        .toList();
  }
}
