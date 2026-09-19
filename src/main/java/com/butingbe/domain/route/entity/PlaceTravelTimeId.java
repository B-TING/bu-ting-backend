package com.butingbe.domain.route.entity;

import com.butingbe.domain.travel.entity.TransportType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캐시 키. 출발·도착 좌표와 교통수단 조합이다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceTravelTimeId implements Serializable {

  @Column(name = "from_key", nullable = false, length = 40)
  private String fromKey;

  @Column(name = "to_key", nullable = false, length = 40)
  private String toKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "transport_type", nullable = false, length = 30)
  private TransportType transportType;

  public PlaceTravelTimeId(String fromKey, String toKey, TransportType transportType) {
    this.fromKey = fromKey;
    this.toKey = toKey;
    this.transportType = transportType;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof PlaceTravelTimeId that)) {
      return false;
    }
    return Objects.equals(fromKey, that.fromKey)
        && Objects.equals(toKey, that.toKey)
        && transportType == that.transportType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromKey, toKey, transportType);
  }
}
