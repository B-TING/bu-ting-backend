package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel-records")
@RequiredArgsConstructor
public class PublicTravelRecordController {

  private final TravelRecordService travelRecordService;

  @GetMapping
  public ResponseEntity<List<TravelRecordFeedResDto>> getLatestFeed() {
    return ResponseEntity.ok(travelRecordService.getLatestFeed());
  }

  @GetMapping("/{travelRecordId}")
  public ResponseEntity<TravelRecordResDto> getPublished(@PathVariable UUID travelRecordId) {
    return ResponseEntity.ok(travelRecordService.getPublished(travelRecordId));
  }
}
