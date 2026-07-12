package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.PlaceReviewImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceReviewImageRepository extends JpaRepository<PlaceReviewImage, UUID> {

  List<PlaceReviewImage> findByPlaceReview_IdOrderBySequenceAsc(UUID placeReviewId);
}
