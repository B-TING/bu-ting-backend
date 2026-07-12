package com.butingbe.domain.travelexpense.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseUpdateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseDetailResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseListResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.service.TravelExpenseService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travels/{travelId}/expenses")
@RequiredArgsConstructor
public class TravelExpenseController {

  private final TravelExpenseService travelExpenseService;

  @GetMapping("/summary")
  public ResponseEntity<TravelExpenseSummaryResponse> getExpenseSummary(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(
        travelExpenseService.getExpenseSummary(user, travelId, from, to));
  }

  @DeleteMapping("/{expenseId}")
  public ResponseEntity<Void> deleteExpense(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID expenseId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelExpenseService.deleteExpense(user, travelId, expenseId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{expenseId}")
  public ResponseEntity<TravelExpenseDetailResponse> updateExpense(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID expenseId,
      @RequestBody @Valid TravelExpenseUpdateRequest request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(
        travelExpenseService.updateExpense(user, travelId, expenseId, request));
  }

  @GetMapping("/{expenseId}")
  public ResponseEntity<TravelExpenseDetailResponse> getExpense(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID expenseId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelExpenseService.getExpense(user, travelId, expenseId));
  }

  @GetMapping
  public ResponseEntity<TravelExpenseListResponse> getExpenses(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestParam(required = false) ExpenseCategory category,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @RequestParam(required = false) UUID payerUserId,
      @PageableDefault(size = 20, sort = "spentAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(
        travelExpenseService.getExpenses(
            user, travelId, category, from, to, payerUserId, pageable));
  }

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
