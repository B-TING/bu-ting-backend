package com.butingbe.domain.travel.entity;

/** 일정 장소가 어디서 왔는지. */
public enum PlanPlaceSource {
  /** 사용자가 위저드나 일정 편집에서 직접 고른 장소. */
  USER_PICKED,
  /** 고른 장소가 모자라 서버가 카탈로그에서 채운 장소. */
  AUTO_FILLED
}
