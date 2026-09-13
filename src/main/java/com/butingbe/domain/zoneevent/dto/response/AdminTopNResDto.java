package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 회차 전체 구역의 Top N 경계 동점 후보 조회 결과. */
public record AdminTopNResDto(String roundId, List<TopNZoneGroupResDto> zones) {}
