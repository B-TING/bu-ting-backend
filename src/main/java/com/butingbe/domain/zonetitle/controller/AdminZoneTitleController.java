package com.butingbe.domain.zonetitle.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderPageResDto;
import com.butingbe.domain.zonetitle.service.AdminZoneTitleService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구역 칭호 정의 관리. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-titles")
@RequiredArgsConstructor
public class AdminZoneTitleController {

  private final AdminZoneTitleService adminZoneTitleService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminZoneTitleDefResDto>>> list(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(ApiResponse.success("칭호 정의 목록", adminZoneTitleService.list(user)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AdminZoneTitleDefResDto>> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid AdminZoneTitleCreateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("칭호 정의 생성", adminZoneTitleService.create(user, request)));
  }

  @PatchMapping("/{titleDefId}")
  public ResponseEntity<ApiResponse<AdminZoneTitleDefResDto>> update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID titleDefId,
      @RequestBody @Valid AdminZoneTitleUpdateReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success("칭호 정의 수정", adminZoneTitleService.update(user, titleDefId, request)));
  }

  @DeleteMapping("/{titleDefId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID titleDefId) {
    adminZoneTitleService.delete(user, titleDefId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{titleDefId}/holders")
  public ResponseEntity<ApiResponse<AdminZoneTitleHolderPageResDto>> holders(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID titleDefId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "칭호 보유자 목록", adminZoneTitleService.holders(user, titleDefId, page, size)));
  }
}
