package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 슬롯의 구역 교체. */
public record SlotReassignReqDto(@NotNull UUID slotId, @NotBlank String zoneId) {}
