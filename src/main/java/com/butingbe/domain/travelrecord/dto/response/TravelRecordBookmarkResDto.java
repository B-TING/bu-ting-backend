package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.TravelRecordBookmark;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRecordBookmarkResDto(
    UUID bookmarkId, LocalDateTime bookmarkedAt, TravelRecordFeedResDto travelRecord) {

  public static TravelRecordBookmarkResDto from(TravelRecordBookmark bookmark) {
    return new TravelRecordBookmarkResDto(
        bookmark.getId(),
        bookmark.getCreatedAt(),
        TravelRecordFeedResDto.from(bookmark.getTravelRecord()));
  }
}
