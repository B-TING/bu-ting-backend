package com.butingbe.domain.route.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRouteService;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans/{planId}/route")
@RequiredArgsConstructor
public class TravelRouteController {

  private final TravelRouteService travelRouteService;

  @GetMapping
  public ResponseEntity<PlanRouteResDto> getPlanRoute(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID planId,
      @RequestParam(required = false) TransportType transportType) {
    return ResponseEntity.ok(travelRouteService.getPlanRoute(user, planId, transportType));
  }
}
