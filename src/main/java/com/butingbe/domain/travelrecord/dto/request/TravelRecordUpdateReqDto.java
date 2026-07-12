package com.butingbe.domain.travelrecord.dto.request;

import jakarta.validation.constraints.Size;

public record TravelRecordUpdateReqDto(
    @Size(max = 100, message = "Travel record title must be 100 characters or less.")
        String title,
    String content,
    @Size(max = 1000, message = "Cover image URL must be 1000 characters or less.")
        String coverImageUrl) {}
