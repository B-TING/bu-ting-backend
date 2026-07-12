package com.butingbe.domain.travelexpense.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.service.TravelExpenseService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travels/{travelId}/expenses")
@RequiredArgsConstructor
public class TravelExpenseController {

  private final TravelExpenseService travelExpenseService;

  @PostMapping
  public ResponseEntity<TravelExpenseCreateResponse> createEqualExpense(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestBody @Valid TravelExpenseCreateRequest request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelExpenseService.createEqualExpense(user, travelId, request));
  }
}
