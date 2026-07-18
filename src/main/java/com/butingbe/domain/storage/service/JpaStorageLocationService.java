package com.butingbe.domain.storage.service;

import com.butingbe.domain.storage.dto.StorageLocationResDto;
import com.butingbe.domain.storage.dto.StorageLocationSearchReqDto;
import com.butingbe.domain.storage.entity.StorageLocation;
import com.butingbe.domain.storage.repository.StorageLocationRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaStorageLocationService implements StorageLocationService {
  private static final double EARTH_RADIUS_METERS = 6_371_000;

  private final StorageLocationRepository repository;

  @Override
  public List<StorageLocationResDto> search(StorageLocationSearchReqDto request) {
    return repository.findAll().stream()
        .map(location -> toResponse(location, request))
        .filter(response -> response.distanceMeters() <= request.radius())
        .sorted(Comparator.comparingInt(StorageLocationResDto::distanceMeters))
        .toList();
  }

  private StorageLocationResDto toResponse(
      StorageLocation location, StorageLocationSearchReqDto request) {
    int distance =
        (int)
            Math.round(
                distanceMeters(
                    request.latitude(),
                    request.longitude(),
                    location.getLatitude(),
                    location.getLongitude()));
    return StorageLocationResDto.from(location, distance);
  }

  private double distanceMeters(
      double latitude, double longitude, double targetLatitude, double targetLongitude) {
    double lat1 = Math.toRadians(latitude);
    double lat2 = Math.toRadians(targetLatitude);
    double dLat = lat2 - lat1;
    double dLon = Math.toRadians(targetLongitude - longitude);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
