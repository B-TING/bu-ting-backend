package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travels/{travelId}/plans/places/{planPlaceId}/review")
@RequiredArgsConstructor
public class PlanPlaceReviewController {

  private final TravelRecordService travelRecordService;

  @PostMapping
  public ResponseEntity<PlaceReviewResDto> createPlaceReview(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID planPlaceId,
      @RequestBody @Valid PlaceReviewCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelRecordService.createPlaceReview(user, travelId, planPlaceId, request));
  }

  @GetMapping
  public ResponseEntity<PlaceReviewResDto> getPlaceReview(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID planPlaceId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.getPlaceReview(user, travelId, planPlaceId));
  }

  @PatchMapping
  public ResponseEntity<PlaceReviewResDto> updatePlaceReview(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID planPlaceId,
      @RequestBody(required = false) @Valid PlaceReviewUpdateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(
        travelRecordService.updatePlaceReview(user, travelId, planPlaceId, request));
  }

  @DeleteMapping
  public ResponseEntity<Void> deletePlaceReview(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID planPlaceId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelRecordService.deletePlaceReview(user, travelId, planPlaceId);
    return ResponseEntity.noContent().build();
  }
}
