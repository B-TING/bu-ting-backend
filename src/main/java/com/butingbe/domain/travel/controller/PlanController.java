package com.butingbe.domain.travel.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
