package com.butingbe.domain.zonetitle.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdminZoneTitleUpdateReqDto(
    String titleName,
    Integer requiredSuccessCount,
    @NotNull Boolean retroactive,
    @NotNull Long expectedRevision) {}
