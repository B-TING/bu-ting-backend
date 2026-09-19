package com.butingbe.domain.place.dto.request;

import com.butingbe.domain.place.entity.PlaceTimeSlot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 장소 보정 요청. null 필드는 바꾸지 않는다.
 *
 * <p>유형별 기본값으로는 담기지 않는 값을 운영이 직접 넣는 통로다. 인기 장소의 실측 체류 시간, 야경·일몰처럼 시간대가 중요한 장소의 배치 기준이 여기 해당한다.
 */
public record PlaceCurationReqDto(
    @Min(10) @Max(480) Integer dwellMinutes, PlaceTimeSlot preferredTimeSlot) {}
