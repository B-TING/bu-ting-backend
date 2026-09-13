package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 이벤트 부분 수정. null 필드는 변경하지 않는다.
 *
 * <p>{@code zoneId/typeCode/startsAt/baseReward}는 SCHEDULED 상태에서만 바꿀 수 있고, ACTIVE에서 시도하면 409다.
 * {@code expectedRevision}이 현재 값과 다르면 409다.
 */
public record AdminZoneEventUpdateReqDto(
    String title,
    String description,
    Integer durationMinutes,
    Integer successLimitPerUser,
    RewardSnapshotReqDto excellenceReward,
    String zoneId,
    String typeCode,
    OffsetDateTime startsAt,
    RewardSnapshotReqDto baseReward,
    AuthTargetPatchReqDto authTarget,
    String reason,
    @NotNull Long expectedRevision) {

  /** ACTIVE에서 바꿀 수 없는 필드가 요청에 들어 있는지. */
  public boolean touchesScheduledOnlyFields() {
    return zoneId != null || typeCode != null || startsAt != null || baseReward != null;
  }

  /** 시간·구역이 바뀌어 겹침 재검증이 필요한지. */
  public boolean touchesTimeOrZone() {
    return zoneId != null || startsAt != null || durationMinutes != null;
  }

  public record AuthTargetPatchReqDto(
      String placeName,
      String guideText,
      String exampleFileKey,
      Double latitude,
      Double longitude,
      Integer radiusM) {}
}
