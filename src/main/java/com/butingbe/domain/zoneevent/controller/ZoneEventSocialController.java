package com.butingbe.domain.zoneevent.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.request.CommentReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReportReqDto;
import com.butingbe.domain.zoneevent.dto.response.CommentPageResDto;
import com.butingbe.domain.zoneevent.dto.response.CommentResDto;
import com.butingbe.domain.zoneevent.dto.response.LikeResDto;
import com.butingbe.domain.zoneevent.dto.response.ReportResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventSocialService;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 참여에 대한 좋아요·댓글·신고. 댓글 조회 외에는 로그인 필요. */
@RestController
@RequestMapping("/zone-event-participations/{participationId}")
@RequiredArgsConstructor
public class ZoneEventSocialController {

  private final ZoneEventSocialService socialService;

  @PostMapping("/likes")
  public ResponseEntity<ApiResponse<LikeResDto>> like(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("좋아요", socialService.like(user, participationId)));
  }

  @DeleteMapping("/likes")
  public ResponseEntity<Void> unlike(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID participationId) {
    socialService.unlike(user, participationId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/comments")
  public ResponseEntity<ApiResponse<CommentResDto>> addComment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestBody @Valid CommentReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "댓글 작성", socialService.addComment(user, participationId, request.content())));
  }

  @GetMapping("/comments")
  public ResponseEntity<ApiResponse<CommentPageResDto>> getComments(
      @PathVariable UUID participationId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(
        ApiResponse.success("댓글 조회", socialService.getComments(participationId, cursor, size)));
  }

  @PatchMapping("/comments/{commentId}")
  public ResponseEntity<ApiResponse<CommentResDto>> editComment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @PathVariable UUID commentId,
      @RequestBody @Valid CommentReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "댓글 수정", socialService.editComment(user, commentId, request.content())));
  }

  @DeleteMapping("/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @PathVariable UUID commentId) {
    socialService.deleteComment(user, commentId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/reports")
  public ResponseEntity<ApiResponse<ReportResDto>> report(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID participationId,
      @RequestBody @Valid ReportReqDto request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "신고 접수",
                socialService.report(user, participationId, request.reasonCode(), request.memo())));
  }
}
