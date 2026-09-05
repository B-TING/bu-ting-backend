package com.butingbe.domain.route.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRouteService;
import com.butingbe.domain.route.dto.response.TravelRouteOptimizeResDto;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travels/{travelId}/route")
@RequiredArgsConstructor
public class TravelRouteOptimizeController {

  private final TravelRouteService travelRouteService;

  /** 여행 전체의 방문 순서를 일자별로 최적화해 제안한다. 일정을 바꾸지는 않는다. */
  @PostMapping("/optimize")
  public ResponseEntity<TravelRouteOptimizeResDto> optimizeTravelRoute(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestParam(required = false) TransportType transportType) {
    return ResponseEntity.ok(
        travelRouteService.optimizeTravelVisitOrder(user, travelId, transportType));
  }
}
