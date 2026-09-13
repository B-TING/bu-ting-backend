package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 확정된 수상자 목록. */
public record WinnerConfirmResDto(
    String eventId, Integer version, List<String> confirmedParticipationIds) {}
