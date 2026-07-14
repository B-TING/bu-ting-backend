package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places/travel-records")
@RequiredArgsConstructor
public class PublicPlaceTravelRecordController {

  private final TravelRecordService travelRecordService;

  @GetMapping
  public ResponseEntity<TravelRecordFeedPageResDto> getTravelRecordsByPlace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam String placeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        travelRecordService.getTravelRecordsByPlace(user, placeId, cursor, size));
  }
}
