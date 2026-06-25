package com.butingbe.domain.travelsurvey.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.travelsurvey.dto.response.TravelSurveyProfileResDto;
import com.butingbe.domain.travelsurvey.service.TravelSurveyService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel-surveys")
@RequiredArgsConstructor
public class TravelSurveyController {

  private final TravelSurveyService travelSurveyService;

  @PutMapping
  public ResponseEntity<TravelSurveyProfileResDto> upsertProfile(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody @Valid TravelSurveyProfileReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelSurveyService.upsertProfile(user.id(), request));
  }

  @GetMapping
  public ResponseEntity<TravelSurveyProfileResDto> getProfile(
      @AuthenticationPrincipal AuthenticatedUser user) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelSurveyService.getProfile(user.id()));
  }
}
