package com.butingbe.domain.travelexpense.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelexpense.dto.response.TravelSettlementResponse;
import com.butingbe.domain.travelexpense.service.TravelSettlementService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travels/{travelId}/expenses/settlements")
@RequiredArgsConstructor
public class TravelSettlementController {

  private final TravelSettlementService travelSettlementService;

  @GetMapping
  public ResponseEntity<TravelSettlementResponse> getSettlement(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }
    return ResponseEntity.ok(travelSettlementService.getSettlement(user, travelId));
  }

  @PostMapping("/confirm")
  public ResponseEntity<TravelSettlementResponse> confirmSettlement(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }
    return ResponseEntity.ok(travelSettlementService.confirmSettlement(user, travelId));
  }
}
