package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** 운영자 최종 수상자 확정 요청. */
public record WinnerConfirmReqDto(
    @NotNull UUID snapshotId,
    @NotEmpty List<UUID> participationIds,
    @NotBlank String selectionReason,
    @NotNull Integer expectedRevision) {}
