package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 한 회차에서 이벤트가 열리는 구역 자리. 회차·구역당 하나(UK). */
@Entity
@Table(
    name = "zone_event_round_slot",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_event_round_slot",
          columnNames = {"round_id", "zone_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventRoundSlot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "slot_id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "round_id", nullable = false)
  private ZoneEventRound round;

  @Enumerated(EnumType.STRING)
  @Column(name = "slot_kind", nullable = false, length = 20)
  private SlotKind slotKind;

  @Column(name = "zone_id", nullable = false, length = 30)
  private String zoneId;

  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "pair_id")
  private UUID pairId;

  @Builder
  private ZoneEventRoundSlot(
      ZoneEventRound round, SlotKind slotKind, String zoneId, UUID eventId, UUID pairId) {
    this.round = round;
    this.slotKind = slotKind;
    this.zoneId = zoneId;
    this.eventId = eventId;
    this.pairId = pairId;
  }

  /** 슬롯에 이벤트를 배정한다. */
  public void assignEvent(UUID eventId) {
    this.eventId = eventId;
  }
}
