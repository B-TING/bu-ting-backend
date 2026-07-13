package com.butingbe.domain.travelrecord.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordBookmarkResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordLikeResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordManageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/travel-records")
@RequiredArgsConstructor
public class PublicTravelRecordController {

  private final TravelRecordService travelRecordService;

  @GetMapping
  public ResponseEntity<TravelRecordFeedPageResDto> getLatestFeed(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return ResponseEntity.ok(travelRecordService.getLatestFeed(cursor, size));
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

  @GetMapping("/{travelRecordId}")
  public ResponseEntity<TravelRecordResDto> getPublished(@PathVariable UUID travelRecordId) {
    return ResponseEntity.ok(travelRecordService.getPublished(travelRecordId));
  }
}
