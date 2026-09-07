package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 이벤트 생성 요청. 인증 타입이면 authTarget이 필요하다. */
public record AdminZoneEventCreateReqDto(
    @NotNull String zoneId,
    @NotNull String typeCode,
    @NotNull String title,
    String description,
    @NotNull OffsetDateTime startsAt,
    @NotNull @Positive Integer durationMinutes,
    UUID roundId,
    Integer successLimitPerUser,
    @NotNull RewardSnapshotReqDto baseReward,
    RewardSnapshotReqDto excellenceReward,
    @Valid AuthTargetReqDto authTarget) {}
