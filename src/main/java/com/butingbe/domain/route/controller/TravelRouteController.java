package com.butingbe.domain.route.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRouteService;
import com.butingbe.domain.route.dto.request.VisitOrderOptimizeReqDto;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.entity.TransportType;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  /** 방문 순서 최적화 결과를 제안한다. 일정을 바꾸지는 않는다. */
  @PostMapping("/optimize")
  public ResponseEntity<VisitOrderResDto> optimizeVisitOrder(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID planId,
      @RequestBody(required = false) @Valid VisitOrderOptimizeReqDto request) {
    VisitOrderOptimizeReqDto body =
        request == null ? new VisitOrderOptimizeReqDto(null, null, null, null) : request;
    return ResponseEntity.ok(
        travelRouteService.optimizeVisitOrder(
            user, planId, body.startPointOrNull(), body.transportType()));
  }
}
