package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.RoundType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** 회차 메타데이터 수정. null 필드는 변경하지 않는다. DRAFT/SCHEDULED 상태에서만 허용된다. */
public record RoundPatchReqDto(
    @NotNull Long expectedRevision,
    String name,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String timezone,
    RoundType roundType) {}
