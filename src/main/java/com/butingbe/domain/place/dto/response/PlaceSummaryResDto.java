package com.butingbe.domain.place.dto.response;

/** 관광지 마스터 데이터 읽기 전용 요약(제목 + 원본 좌표). 인증 타겟 스냅샷 용도. */
public record PlaceSummaryResDto(
    String contentId, String title, Double latitude, Double longitude) {}
