package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
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
@RequestMapping("/travels/{travelId}/records")
@RequiredArgsConstructor
public class TravelRecordController {

  private final TravelRecordService travelRecordService;

  @PostMapping
  public ResponseEntity<TravelRecordResDto> createDraft(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestBody(required = false) @Valid TravelRecordCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelRecordService.createDraft(user, travelId, request));
  }
}
