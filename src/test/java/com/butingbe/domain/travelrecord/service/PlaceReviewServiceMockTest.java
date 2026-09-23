package com.butingbe.domain.travelrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.PlaceReviewImage;
import com.butingbe.domain.travelrecord.repository.PlaceReviewImageRepository;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 리포지토리를 목으로 둬야 만들 수 있는 상태만 다룬다. 나머지는 통합 테스트가 본다. */
@ExtendWith(MockitoExtension.class)
class PlaceReviewServiceMockTest {

  private static final UUID USER_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");
  private static final UUID TRAVEL_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");
  private static final UUID REVIEW_ID = UUID.fromString("66666666-0000-0000-0000-000000000001");

  @Mock private PlaceReviewRepository placeReviewRepository;
  @Mock private PlaceReviewImageRepository placeReviewImageRepository;
  @Mock private FileStorageService fileStorageService;
  @Mock private PlanPlaceRepository planPlaceRepository;
  @Mock private TravelRecordSupport support;

  @InjectMocks private PlaceReviewService placeReviewService;

  private User author;
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    author = user();
    authenticatedUser = new AuthenticatedUser(USER_ID, "author@example.com", "author", List.of());
  }

  @Test
  @DisplayName("파일 키 없이 외부 URL만 가진 리뷰 이미지는 presigned URL을 만들지 않고 그대로 노출한다")
  void usesExternalUrlWhenFileKeyIsAbsent() {
    PlaceReview review = review();
    PlaceReviewImage externalImage =
        PlaceReviewImage.builder()
            .placeReview(review)
            .fileKey(null)
            .externalUrl("https://legacy.example.com/photo.jpg")
            .sequence(1)
            .build();

    when(support.findAuthenticatedUser(authenticatedUser)).thenReturn(author);
    when(planPlaceRepository.findById(any())).thenReturn(Optional.of(planPlace()));
    when(placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(any(), any()))
        .thenReturn(Optional.of(review));
    when(placeReviewImageRepository.findByPlaceReview_IdOrderBySequenceAsc(REVIEW_ID))
        .thenReturn(List.of(externalImage));

    var response =
        placeReviewService.getPlaceReview(authenticatedUser, TRAVEL_ID, UUID.randomUUID());

    assertThat(response.mediaUrls()).containsExactly("https://legacy.example.com/photo.jpg");
    verify(fileStorageService, never()).getPresignedUrl(any());
  }

  private PlaceReview review() {
    PlaceReview review =
        PlaceReview.builder().author(author).rating(5).stayMinutes(60).content("좋았다").build();
    ReflectionTestUtils.setField(review, "id", REVIEW_ID);
    return review;
  }

  private User user() {
    User created =
        User.builder()
            .email("author@example.com")
            .provider("google")
            .providerId("google-author")
            .name(new Name("Kim", "Tester"))
            .nickname("author")
            .role(UserRole.USER)
            .build();
    ReflectionTestUtils.setField(created, "id", USER_ID);
    return created;
  }

  private Travel travel() {
    Travel travel =
        Travel.builder()
            .title("부산")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2026, 9, 3))
            .build();
    ReflectionTestUtils.setField(travel, "id", TRAVEL_ID);
    return travel;
  }

  private PlanPlace planPlace() {
    Plan plan =
        Plan.builder().travel(travel()).dayNumber(1).visitDate(LocalDate.of(2026, 9, 1)).build();
    return PlanPlace.builder()
        .plan(plan)
        .sequence(1)
        .placeName("광안리")
        .address("부산 수영구")
        .latitude(35.153)
        .longitude(129.118)
        .build();
  }
}
