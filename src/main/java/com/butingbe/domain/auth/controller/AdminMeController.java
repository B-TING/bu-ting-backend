package com.butingbe.domain.auth.controller;

import com.butingbe.domain.auth.dto.response.AdminMeResDto;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.service.AdminMeService;
import com.butingbe.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 페이지 진입 정보. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMeController {

  private final AdminMeService adminMeService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<AdminMeResDto>> getMe(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(ApiResponse.success("관리자 정보 조회", adminMeService.getMe(user)));
  }
}
