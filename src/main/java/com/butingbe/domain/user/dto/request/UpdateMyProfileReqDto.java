package com.butingbe.domain.user.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateMyProfileReqDto(
    @Size(max = 50) String nickname,
    @Size(max = 500) String profileImageUrl,
    @Size(max = 50) String firstName,
    @Size(max = 20) String lastName) {}
