package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이벤트 타입 시드(PLACE_AUTH, OBJECT_AUTH …). type_code가 PK다. */
@Entity
@Table(name = "zone_event_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventType extends TimestampEntity {

  @Id
  @Column(name = "type_code", length = 30)
  private String typeCode;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "requires_upload", nullable = false)
  private Boolean requiresUpload;

  @Column(columnDefinition = "text")
  private String description;

  @Builder
  private ZoneEventType(String typeCode, String name, Boolean requiresUpload, String description) {
    this.typeCode = typeCode;
    this.name = name;
    this.requiresUpload = requiresUpload;
    this.description = description;
  }
}
