package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 회차의 구역(이벤트) 하나에 대한 Top N 후보 묶음. */
public record TopNZoneGroupResDto(
    String eventId, String zoneId, Integer version, List<TopNCandidateResDto> candidates) {}
