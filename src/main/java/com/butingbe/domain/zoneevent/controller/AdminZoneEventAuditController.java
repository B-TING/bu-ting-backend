package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventAuditPageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventAuditService;
import com.butingbe.global.common.ApiResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 행위 감사 이력 조회. ROLE_ADMIN/MANAGER 전용(서비스에서 검사). */
@RestController
@RequestMapping("/admin/zone-event-audits")
@RequiredArgsConstructor
public class AdminZoneEventAuditController {

  private final AdminZoneEventAuditService adminZoneEventAuditService;

  @GetMapping
  public ResponseEntity<ApiResponse<AdminZoneEventAuditPageResDto>> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) UUID resourceId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "감사 이력 목록",
            adminZoneEventAuditService.list(
                user, resourceType, resourceId, actorId, from, to, page, size)));
  }
}
