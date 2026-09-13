package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.RewardCatalog;

/** 보상 카탈로그 응답. */
public record RewardCatalogResDto(
    String rewardId,
    String rewardType,
    String code,
    String name,
    Integer pointAmount,
    String imageFileKey,
    Integer stock,
    Integer monthlyCap,
    Integer validDays,
    boolean active) {

  public static RewardCatalogResDto from(RewardCatalog catalog) {
    return new RewardCatalogResDto(
        catalog.getId().toString(),
        catalog.getRewardType().name(),
        catalog.getCode(),
        catalog.getName(),
        catalog.getPointAmount(),
        catalog.getImageFileKey(),
        catalog.getStock(),
        catalog.getMonthlyCap(),
        catalog.getValidDays(),
        Boolean.TRUE.equals(catalog.getActive()));
  }
}
