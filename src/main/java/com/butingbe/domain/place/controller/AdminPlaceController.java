package com.butingbe.domain.place.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.place.dto.request.PlaceCurationReqDto;
import com.butingbe.domain.place.dto.response.PlaceCurationResDto;
import com.butingbe.domain.place.dto.response.PlaceEnrichResDto;
import com.butingbe.domain.place.dto.response.PlaceSyncResDto;
import com.butingbe.domain.place.service.PlaceCurationService;
import com.butingbe.domain.place.service.PlaceEnrichmentService;
import com.butingbe.domain.place.service.PlaceSyncService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 장소 카탈로그 운영 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController {

  private final PlaceSyncService placeSyncService;
  private final PlaceEnrichmentService placeEnrichmentService;
  private final PlaceCurationService placeCurationService;

  @PostMapping("/sync")
  public ResponseEntity<ApiResponse<PlaceSyncResDto>> sync(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(ApiResponse.success("장소 카탈로그 동기화", placeSyncService.sync(user)));
  }

  @PostMapping("/enrich")
  public ResponseEntity<ApiResponse<PlaceEnrichResDto>> enrich(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(
        ApiResponse.success("장소 인기도 보강", placeEnrichmentService.enrich(user, limit)));
  }

  @PatchMapping("/{placeId}")
  public ResponseEntity<ApiResponse<PlaceCurationResDto>> curate(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID placeId,
      @RequestBody @Valid PlaceCurationReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("장소 보정", placeCurationService.curate(user, placeId, request)));
  }
}
