package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewSummaryResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places/reviews")
@RequiredArgsConstructor
public class PublicPlaceReviewController {

  private final TravelRecordService travelRecordService;

  @GetMapping
  public ResponseEntity<PlaceReviewSummaryResDto> getPlaceReviewSummary(
      @RequestParam PlaceProvider provider, @RequestParam String providerPlaceId) {
    return ResponseEntity.ok(travelRecordService.getPlaceReviewSummary(provider, providerPlaceId));
  }
}
