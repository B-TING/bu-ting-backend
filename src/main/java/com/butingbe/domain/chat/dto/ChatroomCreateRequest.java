package com.butingbe.domain.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ChatroomCreateRequest(
    @NotBlank(message = "채팅방 제목은 필수입니다.") String title,
    String description,
    @NotBlank(message = "행정코드는 필수입니다.") String localCode,
    @NotBlank(message = "채팅 권역(Zone)은 필수입니다.") String chatZone,
    @Min(value = 10, message = "최소 정원은 10명 이상입니다.")
        @Max(value = 500, message = "최대 정원은 500명 이하입니다.")
        Integer maxMembers) {}
