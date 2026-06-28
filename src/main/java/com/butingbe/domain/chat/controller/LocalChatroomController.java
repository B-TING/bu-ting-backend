package com.butingbe.domain.chat.controller;

import com.butingbe.domain.chat.dto.ChatroomCreateRequest;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.service.LocalChatroomService;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class LocalChatroomController {

    private final LocalChatroomService localChatroomService;


    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatroomResponse>>> getRoomsWithinBounds(
            @RequestParam BigDecimal swLat,
            @RequestParam BigDecimal swLng,
            @RequestParam BigDecimal neLat,
            @RequestParam BigDecimal neLng
    ) {
        List<ChatroomResponse> rooms = localChatroomService.getRoomsWithinBounds(swLat, swLng, neLat, neLng);
        return ResponseEntity.ok(ApiResponse.success("지역별 채팅방 조회", rooms));
    }

    @GetMapping("/zone")
    public ResponseEntity<ApiResponse<List<ChatroomResponse>>> getRoomsByZone(
            @RequestParam String zone
    ) {
        // 프론트엔드에서 받은 문자열을 안전하게 Enum 객체로 변환
        ChatZone chatZone = ChatZone.fromString(zone);
        List<ChatroomResponse> rooms = localChatroomService.getRoomsByZone(chatZone);
        return ResponseEntity.ok(ApiResponse.success("지역별 채팅방 조회", rooms));
    }

    @PostMapping("/{room_id}/join")
    public ResponseEntity<ApiResponse<Void>> joinChatroom(@PathVariable UUID roomId, @AuthenticationPrincipal User loginUser) {

        localChatroomService.joinChatroom(roomId, loginUser.getId());
        return ResponseEntity.ok(ApiResponse.success("채팅방 성공",null));
    }

    @DeleteMapping("/{room_id}/exit")
    public ResponseEntity<ApiResponse<Void>> exitRoom(@PathVariable UUID roomId, @AuthenticationPrincipal User loginUser) {

        localChatroomService.exitChatroom(roomId, loginUser.getId());
        return ResponseEntity.ok(ApiResponse.success("채팅방 나가기 완료", null));
    }
}
