package com.butingbe.domain.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ChatroomCreateRequest(
        @NotBlank(message = "채팅방 제목은 필수입니다.")
        String title,

        String description,

        @NotBlank(message = "법정동 행정코드는 필수입니다.")
        String localCode,

        @NotBlank(message = "Google Place ID는 필수입니다.")
        String googlePlaceId,

        @NotBlank(message = "랜드마크 명칭은 필수입니다.")
        String landmarkName,

        @NotNull(message = "위도는 필수입니다.")
        BigDecimal latitude,

        @NotNull(message = "경도는 필수입니다.")
        BigDecimal longitude,

        @Min(value = 10, message = "최소 정원은 10명 이상입니다.")
        @Max(value = 500, message = "최대 정원은 500명 이하입니다.")
        Integer maxMembers
) {}
