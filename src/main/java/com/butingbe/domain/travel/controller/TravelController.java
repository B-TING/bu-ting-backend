package com.butingbe.domain.travel.controller;

import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.service.TravelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travels")
@RequiredArgsConstructor
public class TravelController {

  private final TravelService travelService;

  @PostMapping
  public ResponseEntity<TravelResDto> createTravel(
      @RequestBody @Valid TravelCreateReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(travelService.createTravel(request));
  }
}
