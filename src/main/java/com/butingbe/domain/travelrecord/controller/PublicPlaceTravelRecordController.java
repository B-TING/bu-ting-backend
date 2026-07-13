package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<List<TravelRecordFeedResDto>> getTravelRecordsByPlace(
      @RequestParam PlaceProvider provider, @RequestParam String providerPlaceId) {
    return ResponseEntity.ok(
        travelRecordService.getTravelRecordsByPlace(provider, providerPlaceId));
  }
}
