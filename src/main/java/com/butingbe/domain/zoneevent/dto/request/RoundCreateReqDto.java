package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.RoundType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/** 회차 생성. zoneIds에 대해 AUTH 슬롯을 만든다. */
public record RoundCreateReqDto(
    RoundType roundType,
    @NotNull OffsetDateTime startsAt,
    @NotNull OffsetDateTime endsAt,
    String timezone,
    @NotEmpty List<String> zoneIds) {}
