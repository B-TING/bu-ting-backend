package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.RoundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** 회차 초안(DRAFT) 생성. 구역 슬롯은 이후 POST /admin/zone-events로 개별 추가한다. */
public record RoundCreateReqDto(
    RoundType roundType,
    @NotNull Integer roundNo,
    String name,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime endsAt,
    String timezone,
    @Valid RewardSnapshotReqDto excellenceReward) {}
