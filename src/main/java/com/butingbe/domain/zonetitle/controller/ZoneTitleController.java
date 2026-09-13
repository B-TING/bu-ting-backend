package com.butingbe.domain.zonetitle.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import com.butingbe.domain.zonetitle.dto.response.MyZoneTitlesResDto;
import com.butingbe.domain.zonetitle.dto.response.ZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import com.butingbe.global.common.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 구역 칭호 조회·장착. 정의 조회는 비로그인 허용, 나머지는 로그인 필요. */
@RestController
@RequiredArgsConstructor
public class ZoneTitleController {

  private final ZoneTitleService zoneTitleService;

  @GetMapping("/users/me/zone-titles")
  public ResponseEntity<ApiResponse<MyZoneTitlesResDto>> myTitles(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return ResponseEntity.ok(ApiResponse.success("내 칭호 조회", zoneTitleService.myTitles(user)));
  }

  @PatchMapping("/users/me/zone-titles/{userTitleId}/equip")
  public ResponseEntity<ApiResponse<EquippedTitleResDto>> equip(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID userTitleId) {
    return ResponseEntity.ok(
        ApiResponse.success("대표 칭호 장착", zoneTitleService.equip(user, userTitleId)));
  }

  @DeleteMapping("/users/me/zone-titles/equipped")
  public ResponseEntity<Void> unequip(@AuthenticationPrincipal AuthenticatedUser user) {
    zoneTitleService.unequip(user);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/zone-titles")
  public ResponseEntity<ApiResponse<List<ZoneTitleDefResDto>>> allDefs() {
    return ResponseEntity.ok(ApiResponse.success("칭호 정의 조회", zoneTitleService.allDefs()));
  }
}
