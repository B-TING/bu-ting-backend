package com.butingbe.domain.station.entity;

import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "station")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Station extends TimestampEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String operator;

  @Column(nullable = false, length = 30)
  private String line;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;
}
