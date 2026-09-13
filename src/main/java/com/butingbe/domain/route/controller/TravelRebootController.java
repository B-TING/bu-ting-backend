package com.butingbe.domain.route.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRebootService;
import com.butingbe.domain.route.dto.request.TravelRebootReqDto;
import com.butingbe.domain.route.dto.response.TravelRebootResDto;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plans/{planId}/reboot")
@RequiredArgsConstructor
public class TravelRebootController {

  private final TravelRebootService travelRebootService;

  /** 현재 위치와 남은 시간으로 그날의 남은 일정을 다시 짜 제안한다. 일정을 바꾸지는 않는다. */
  @PostMapping
  public ResponseEntity<TravelRebootResDto> reboot(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID planId,
      @RequestBody @Valid TravelRebootReqDto request) {
    return ResponseEntity.ok(
        travelRebootService.reboot(
            user,
            planId,
            request.currentPoint(),
            request.availableMinutes(),
            request.transportType()));
  }
}
