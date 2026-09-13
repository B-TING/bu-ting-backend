package com.butingbe.domain.reward.dto.request;

/** 보상 카탈로그 부분 수정. null은 변경하지 않는다. */
public record AdminRewardCatalogUpdateReqDto(
    String name, Integer stock, Integer monthlyCap, Boolean active) {}
