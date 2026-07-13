package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.TravelRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRecordFeedResDto(
    UUID travelRecordId,
    UUID originalTravelId,
    UUID authorId,
    String authorNickname,
    String title,
    String content,
    String coverImageUrl,
    LocalDate travelStartDate,
    LocalDate travelEndDate,
    LocalDateTime publishedAt,
    long likeCount,
    long viewCount) {

  public static TravelRecordFeedResDto from(TravelRecord travelRecord) {
    return new TravelRecordFeedResDto(
        travelRecord.getId(),
        travelRecord.getOriginalTravel() == null ? null : travelRecord.getOriginalTravel().getId(),
        travelRecord.getAuthor().getId(),
        travelRecord.getAuthor().getNickname(),
        travelRecord.getTitle(),
        travelRecord.getContent(),
        travelRecord.getCoverImageUrl(),
        travelRecord.getTravelStartDate(),
        travelRecord.getTravelEndDate(),
        travelRecord.getPublishedAt(),
        travelRecord.getLikeCount(),
        travelRecord.getViewCount());
  }
}
