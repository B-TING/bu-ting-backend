package com.butingbe.domain.storage.entity;

import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "locker_fee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LockerFee extends TimestampEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "locker_location_id", nullable = false)
  private StorageLocation lockerLocation;

  @Column(name = "schedule_type", nullable = false, length = 50)
  private String scheduleType;

  @Column(name = "locker_size", nullable = false, length = 30)
  private String lockerSize;

  @Column(nullable = false)
  private Integer amount;

  @Column(name = "billing_unit", nullable = false, length = 50)
  private String billingUnit;
}
