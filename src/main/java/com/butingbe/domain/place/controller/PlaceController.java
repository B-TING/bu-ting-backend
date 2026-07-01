package com.butingbe.domain.place.controller;

import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import com.butingbe.domain.place.service.PlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

  private final PlaceService placeService;

  @GetMapping
  public ResponseEntity<PlaceSearchResDto> searchPlaces(
      @ModelAttribute @Valid PlaceSearchReqDto request) {
    return ResponseEntity.ok(placeService.searchPlaces(request));
  }

  @GetMapping("/{contentId}/detail")
  public ResponseEntity<PlaceDetailResDto> getPlaceDetail(
      @PathVariable String contentId,
      @RequestParam String contentTypeId,
      @RequestParam(required = false) String googleSearchText) {
    return ResponseEntity.ok(
        placeService.getPlaceDetail(contentId, contentTypeId, googleSearchText));
  }
}
