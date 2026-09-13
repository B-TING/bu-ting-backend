package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 보상 카탈로그 생성 요청. */
public record AdminRewardCatalogCreateReqDto(
    @NotNull String rewardType,
    @NotBlank String code,
    @NotBlank String name,
    Integer pointAmount,
    String imageFileKey,
    Integer stock,
    Integer monthlyCap,
    Integer validDays,
    Boolean active) {}
