package com.butingbe.domain.travelteam.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.dto.TravelMemberResponse;
import com.butingbe.domain.travelteam.dto.request.TravelLeaderTransferRequest;
import com.butingbe.domain.travelteam.service.TravelTeamService;
import com.butingbe.global.common.ApiResponse;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/travel/team")
public class TravelTeamController {

  private final TravelTeamService travelTeamService;

  @GetMapping("/{travelId}/members")
  public ResponseEntity<ApiResponse<List<TravelMemberResponse>>> getTravelMembers(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    List<TravelMemberResponse> response = travelTeamService.getTravelMembers(user, travelId);
    return ResponseEntity.ok(ApiResponse.success("Travel members retrieved.", response));
  }

  @PatchMapping("/{travelId}/leader")
  public ResponseEntity<ApiResponse<Void>> transferLeader(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @RequestBody @Valid TravelLeaderTransferRequest request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelTeamService.transferLeader(user, travelId, request);
    return ResponseEntity.ok(ApiResponse.success("Travel leader transferred.", null));
  }

  @DeleteMapping("/{travelId}/members/{userId}")
  public ResponseEntity<ApiResponse<Void>> removeMember(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelId,
      @PathVariable UUID userId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelTeamService.removeMember(user, travelId, userId);
    return ResponseEntity.ok(ApiResponse.success("Travel member removed.", null));
  }

  @GetMapping("/invites/verify")
  public ResponseEntity<ApiResponse<InviteVerificationResponse>> verifyInvite(
      @RequestParam("token") String token) {
    InviteVerificationResponse response = travelTeamService.verifyToken(token);
    return ResponseEntity.ok(ApiResponse.success("Invite token verified.", response));
  }

  @PostMapping("/{travelId}/invite")
  public ResponseEntity<Map<String, String>> createInviteLink(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    String inviteLink = travelTeamService.createInviteLink(user, travelId);
    Map<String, String> response = new HashMap<>();
    response.put("inviteLink", inviteLink);

    return ResponseEntity.ok(response);
  }

  @PostMapping("/invites/accept")
  public ResponseEntity<ApiResponse<InviteVerificationResponse>> acceptInvite(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestParam("token") String token) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    InviteVerificationResponse response = travelTeamService.acceptInvite(user, token);
    return ResponseEntity.ok(ApiResponse.success("Invite accepted.", response));
  }

  @DeleteMapping("/{travelId}/members/me")
  public ResponseEntity<ApiResponse<Void>> exitTravel(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelTeamService.exitTravel(user, travelId);
    return ResponseEntity.ok(ApiResponse.success("Travel exit completed.", null));
  }
}
