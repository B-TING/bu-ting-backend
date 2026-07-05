package com.butingbe.domain.travel.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid TravelCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(travelService.createTravel(user, request));
  }

  @GetMapping("/{travelId}/plans")
  public ResponseEntity<TravelPlansResDto> getTravelPlans(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelService.getTravelPlans(user, travelId));
  }

  @PostMapping("/{travelId}/plans")
  public ResponseEntity<PlanResDto> createPlan(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestBody @Valid PlanCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelService.createPlan(user, travelId, request));
  }

  @PostMapping("/{travelId}/plans/{planId}/places")
  public ResponseEntity<PlanPlaceResDto> createPlanPlace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID planId,
      @RequestBody @Valid PlanPlaceCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelService.createPlanPlace(user, travelId, planId, request));
  }

  @DeleteMapping("/{travelId}/plans/{planId}/places/{planPlaceId}")
  public ResponseEntity<Void> deletePlanPlace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID planId,
      @PathVariable UUID planPlaceId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelService.deletePlanPlace(user, travelId, planId, planPlaceId);
    return ResponseEntity.noContent().build();
  }
}
