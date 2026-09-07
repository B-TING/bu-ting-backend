package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.VisibilityUpdateReqDto;
import com.butingbe.domain.zoneevent.dto.response.AlbumPageResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventAlbumService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 앨범 조회(이벤트·구역·회차)와 참여 공개 설정. 조회는 비로그인 허용, 설정은 로그인 필요. */
@RestController
@RequiredArgsConstructor
public class ZoneEventAlbumController {

  private final ZoneEventAlbumService albumService;

  @GetMapping("/zone-events/{eventId}/album")
  public ResponseEntity<ApiResponse<AlbumPageResDto>> eventAlbum(
      @PathVariable UUID eventId,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "이벤트 앨범 조회", albumService.eventAlbum(eventId, sort, cursor, size, viewerId(user))));
  }

  @GetMapping("/zones/{zoneId}/album")
  public ResponseEntity<ApiResponse<AlbumPageResDto>> zoneAlbum(
      @PathVariable String zoneId,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "구역 앨범 조회", albumService.zoneAlbum(zoneId, sort, cursor, size, viewerId(user))));
  }

  @GetMapping("/zone-event-rounds/{roundId}/album")
  public ResponseEntity<ApiResponse<AlbumPageResDto>> roundAlbum(
      @PathVariable UUID roundId,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size,
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "회차 앨범 조회", albumService.roundAlbum(roundId, sort, cursor, size, viewerId(user))));
  }

  @PatchMapping("/zone-event-participations/{participationId}/visibility")
  public ResponseEntity<ApiResponse<Void>> setVisibility(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestBody @Valid VisibilityUpdateReqDto request) {
    albumService.setVisibility(user, participationId, request.visibility());
    return ResponseEntity.ok(ApiResponse.success("공개 설정 변경", null));
  }

  private UUID viewerId(AuthenticatedUser user) {
    return user == null ? null : user.id();
  }
}
