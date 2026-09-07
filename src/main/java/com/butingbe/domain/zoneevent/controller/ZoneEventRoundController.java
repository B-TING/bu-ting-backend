package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.zoneevent.dto.response.RoundStatusResDto;
import com.butingbe.domain.zoneevent.service.RoundStatusQueryService;
import com.butingbe.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 유저 회차 현황(FR-EVT-03). */
@RestController
@RequestMapping("/zone-event-rounds")
@RequiredArgsConstructor
public class ZoneEventRoundController {

  private final RoundStatusQueryService roundStatusQueryService;

  @GetMapping("/current")
  public ResponseEntity<ApiResponse<RoundStatusResDto>> current() {
    return ResponseEntity.ok(ApiResponse.success("회차 현황", roundStatusQueryService.current()));
  }
}
