package com.butingbe.domain.travel.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

  private final TravelService travelService;

  @GetMapping("/{planId}/places")
  public ResponseEntity<List<PlanPlaceResDto>> getPlanPlaces(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID planId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelService.getPlanPlaces(user, planId));
  }

  @PostMapping("/{planId}/places")
  public ResponseEntity<PlanPlaceResDto> createPlanPlace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID planId,
      @RequestBody @Valid PlanPlaceCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelService.createPlanPlace(user, planId, request));
  }

  @PatchMapping("/places/{planPlaceId}")
  public ResponseEntity<PlanPlaceResDto> updatePlanPlace(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID planPlaceId,
      @RequestBody PlanPlaceUpdateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelService.updatePlanPlace(user, planPlaceId, request));
  }

  @DeleteMapping("/places/{planPlaceId}")
  public ResponseEntity<Void> deletePlanPlace(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID planPlaceId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelService.deletePlanPlace(user, planPlaceId);
    return ResponseEntity.noContent().build();
  }
}
