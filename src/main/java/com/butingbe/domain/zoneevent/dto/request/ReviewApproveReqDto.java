package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 검수 승인 요청. 재제출로 밀려난 이전 submissionId나 오래된 expectedRevision이면 409다. */
public record ReviewApproveReqDto(@NotNull UUID submissionId, @NotNull Long expectedRevision) {}
