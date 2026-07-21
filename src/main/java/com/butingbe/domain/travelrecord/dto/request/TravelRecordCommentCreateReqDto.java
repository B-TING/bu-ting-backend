package com.butingbe.domain.travelrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TravelRecordCommentCreateReqDto(
    @NotBlank(message = "Travel record comment content is required.")
        @Size(
            max = 1000,
            message = "Travel record comment content must be 1000 characters or less.")
        String content) {}
