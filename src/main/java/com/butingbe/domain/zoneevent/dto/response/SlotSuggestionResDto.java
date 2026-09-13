package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 자동 슬롯 배정 제안. 배정할 구역 목록과 근거. */
public record SlotSuggestionResDto(List<String> slots, List<String> rationale) {}
