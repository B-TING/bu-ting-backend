package com.butingbe.domain.place.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.place.dto.response.PlaceSyncResDto;
import com.butingbe.domain.place.service.PlaceSyncService;
import com.butingbe.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 장소 카탈로그 운영 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController {

  private final PlaceSyncService placeSyncService;

  @PostMapping("/sync")
  public ResponseEntity<ApiResponse<PlaceSyncResDto>> sync(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(ApiResponse.success("장소 카탈로그 동기화", placeSyncService.sync(user)));
  }
}
