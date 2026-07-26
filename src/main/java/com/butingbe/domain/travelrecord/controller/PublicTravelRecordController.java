package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordFeedSort;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordBookmarkResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordCommentResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordLikeResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordManageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/travel-records")
@RequiredArgsConstructor
public class PublicTravelRecordController {

  private final TravelRecordService travelRecordService;

  @GetMapping
  public ResponseEntity<TravelRecordFeedPageResDto> getLatestFeed(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String placeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate travelStartDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate travelEndDate,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) TravelRecordFeedSort sort) {
    return ResponseEntity.ok(
        travelRecordService.getLatestFeed(
            user,
            cursor,
            size,
            keyword,
            placeId,
            travelStartDate,
            travelEndDate,
            region,
            city,
            sort));
  }

  @GetMapping("/me")
  public ResponseEntity<List<TravelRecordManageResDto>> getMyRecords(
      @AuthenticationPrincipal AuthenticatedUser user) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.getMyRecords(user));
  }

  @GetMapping("/me/bookmarks")
  public ResponseEntity<List<TravelRecordBookmarkResDto>> getMyBookmarkedRecords(
      @AuthenticationPrincipal AuthenticatedUser user) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.getMyBookmarkedRecords(user));
  }

  @GetMapping("/me/{travelRecordId}")
  public ResponseEntity<TravelRecordResDto> getMyRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.getMyRecord(user, travelRecordId));
  }

  @PatchMapping("/me/{travelRecordId}")
  public ResponseEntity<TravelRecordResDto> updateMyRecord(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelRecordId,
      @RequestBody(required = false) @Valid TravelRecordUpdateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.updateMyRecord(user, travelRecordId, request));
  }

  @PostMapping("/me/{travelRecordId}/hide")
  public ResponseEntity<TravelRecordResDto> hideMyRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.hideMyRecord(user, travelRecordId));
  }

  @PostMapping("/me/{travelRecordId}/republish")
  public ResponseEntity<TravelRecordResDto> republishMyRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(travelRecordService.republishMyRecord(user, travelRecordId));
  }

  @PostMapping("/{travelRecordId}/bookmarks")
  public ResponseEntity<TravelRecordBookmarkResDto> bookmarkTravelRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelRecordService.bookmarkTravelRecord(user, travelRecordId));
  }

  @DeleteMapping("/{travelRecordId}/bookmarks")
  public ResponseEntity<Void> unbookmarkTravelRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelRecordService.unbookmarkTravelRecord(user, travelRecordId);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{travelRecordId}/likes")
  public ResponseEntity<TravelRecordLikeResDto> likeTravelRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelRecordService.likeTravelRecord(user, travelRecordId));
  }

  @DeleteMapping("/{travelRecordId}/likes")
  public ResponseEntity<Void> unlikeTravelRecord(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelRecordService.unlikeTravelRecord(user, travelRecordId);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{travelRecordId}/comments")
  public ResponseEntity<TravelRecordCommentResDto> createComment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelRecordId,
      @RequestBody @Valid TravelRecordCommentCreateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(travelRecordService.createComment(user, travelRecordId, request));
  }

  @GetMapping("/{travelRecordId}/comments")
  public ResponseEntity<List<TravelRecordCommentResDto>> getComments(
      @PathVariable UUID travelRecordId) {
    return ResponseEntity.ok(travelRecordService.getComments(travelRecordId));
  }

  @PatchMapping("/{travelRecordId}/comments/{commentId}")
  public ResponseEntity<TravelRecordCommentResDto> updateComment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelRecordId,
      @PathVariable UUID commentId,
      @RequestBody @Valid TravelRecordCommentUpdateReqDto request) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    return ResponseEntity.ok(
        travelRecordService.updateComment(user, travelRecordId, commentId, request));
  }

  @DeleteMapping("/{travelRecordId}/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID travelRecordId,
      @PathVariable UUID commentId) {
    if (user == null) {
      throw new UnauthenticatedException();
    }

    travelRecordService.deleteComment(user, travelRecordId, commentId);

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{travelRecordId}")
  public ResponseEntity<TravelRecordResDto> getPublished(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID travelRecordId) {
    return ResponseEntity.ok(travelRecordService.getPublished(user, travelRecordId));
  }
}
