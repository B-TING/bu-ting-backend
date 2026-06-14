package com.butingbe.domain.user.dto.request;

import com.butingbe.domain.user.entity.Name;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpReqDto(
    @NotBlank(message = "이메일은 필수 입력 항목입니다.") @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 100)
        String email,
    @NotBlank(message = "닉네임은 필수 입력 항목입니다.") @Size(max = 50) String nickname,

    // 소셜 연동 정보 (선택 혹은 임시 목업용)
    String provider,
    String providerId,
    String firstName,
    String lastName) {
  public Name toEmbeddedName() {
    return new Name(firstName != null ? firstName : "", lastName != null ? lastName : "");
  }
}
