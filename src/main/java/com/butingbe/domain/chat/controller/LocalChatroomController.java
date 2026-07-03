package com.butingbe.domain.chat.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.service.LocalChatroomService;
import com.butingbe.global.common.ApiResponse;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/rooms")
public class LocalChatroomController {

  private final LocalChatroomService localChatroomService;

  @GetMapping("/zone")
  public ResponseEntity<ApiResponse<List<ChatroomResponse>>> getRoomsByZone(
      @RequestParam String zone) {
    // 프론트엔드에서 받은 문자열을 안전하게 Enum 객체로 변환
    ChatZone chatZone = ChatZone.fromString(zone);
    List<ChatroomResponse> rooms = localChatroomService.getRoomsByZone(chatZone);
    return ResponseEntity.ok(ApiResponse.success("지역별 채팅방 조회", rooms));
  }

  @PostMapping("/{roomId}/join")
  public ResponseEntity<ApiResponse<Void>> joinChatroom(@PathVariable UUID roomId,
                                                               @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null) {
      throw new UnauthenticatedException();
    }
    localChatroomService.joinRoom(roomId, authenticatedUser.id());

    return ResponseEntity.ok(ApiResponse.success("채팅방 가입 완료", null));
  }

  @DeleteMapping("/{roomId}/exit")
  public ResponseEntity<ApiResponse<Void>> exitRoom(
      @PathVariable UUID roomId, @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null) {
      throw new UnauthenticatedException();
    }
    localChatroomService.exitChatroom(roomId, authenticatedUser.id());
    return ResponseEntity.ok(ApiResponse.success("채팅방 나가기 완료", null));
  }

  @GetMapping("/{roomId}/messages")
  public ResponseEntity<List<ChatMessageResponse>> getMessages(
      @PathVariable UUID roomId, @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null) {
      throw new UnauthenticatedException();
    }

    // 과거 내역 조회 수행
    List<ChatMessageResponse> history =
        localChatroomService.getChatRoom(roomId, authenticatedUser.id());

    // 200 OK 상태코드와 함께 과거 메시지 리스트 반환
    return ResponseEntity.ok(history);
  }
}
