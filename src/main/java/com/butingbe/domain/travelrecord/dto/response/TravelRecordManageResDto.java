package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRecordManageResDto(
    UUID travelRecordId,
    UUID originalTravelId,
    UUID authorId,
    String title,
    String content,
    String coverImageUrl,
    LocalDate travelStartDate,
    LocalDate travelEndDate,
    TravelRecordStatus status,
    LocalDateTime publishedAt,
    long likeCount,
    long viewCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TravelRecordManageResDto from(TravelRecord travelRecord) {
    return new TravelRecordManageResDto(
        travelRecord.getId(),
        travelRecord.getOriginalTravel() == null ? null : travelRecord.getOriginalTravel().getId(),
        travelRecord.getAuthor().getId(),
        travelRecord.getTitle(),
        travelRecord.getContent(),
        travelRecord.getCoverImageUrl(),
        travelRecord.getTravelStartDate(),
        travelRecord.getTravelEndDate(),
        travelRecord.getStatus(),
        travelRecord.getPublishedAt(),
        travelRecord.getLikeCount(),
        travelRecord.getViewCount(),
        travelRecord.getCreatedAt(),
        travelRecord.getUpdatedAt());
  }
}
