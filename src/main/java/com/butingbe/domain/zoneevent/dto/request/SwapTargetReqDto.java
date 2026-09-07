package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 예비 타겟으로 ACTIVE 이벤트의 인증 타겟을 즉시 교체(우천 대응, FR-RND-05). */
public record SwapTargetReqDto(@NotNull UUID eventId, @NotNull UUID backupTargetId) {}
