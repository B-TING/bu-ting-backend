package com.butingbe.domain.travelteam.controller;

import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.service.TravelTeamService;
import com.butingbe.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/travel/team")
public class TravelTeamController {
    private final TravelTeamService travelTeamService;

    @GetMapping("/invites/verify")
    public ResponseEntity<ApiResponse<InviteVerificationResponse>> verifyInvite(@RequestParam("token") String token) {
        InviteVerificationResponse response = travelTeamService.verifyToken(token);
        return ResponseEntity.ok(ApiResponse.success("토큰 검증 완료" ,response));
    }

    @PostMapping("/{teamId}/invite")
    public ResponseEntity<Map<String, String>> createInviteLink(@PathVariable("teamId") Long teamId) {
        String inviteLink = travelTeamService.createInviteLink(teamId);

        Map<String, String> response = new HashMap<>();
        response.put("inviteLink", inviteLink);

        return ResponseEntity.ok(response);
    }
}
