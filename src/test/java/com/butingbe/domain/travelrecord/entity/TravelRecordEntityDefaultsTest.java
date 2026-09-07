package com.butingbe.domain.travelrecord.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TravelRecordEntityDefaultsTest {

  @Test
  @DisplayName("상태와 카운트를 지정하지 않으면 DRAFT와 0으로 시작한다")
  void travelRecordFallsBackToDefaults() {
    TravelRecord travelRecord =
        TravelRecord.builder()
            .author(author())
            .title("부산 3일")
            .travelStartDate(LocalDate.of(2026, 9, 1))
            .travelEndDate(LocalDate.of(2026, 9, 3))
            .build();

    assertThat(travelRecord.getStatus()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(travelRecord.getLikeCount()).isZero();
    assertThat(travelRecord.getViewCount()).isZero();
  }

  @Test
  @DisplayName("평점을 넘기지 않는 updateContent는 기존 평점을 유지한다")
  void updateContentWithoutRatingKeepsExistingRating() {
    TravelRecord travelRecord =
        TravelRecord.builder().author(author()).title("이전 제목").overallRating(4).build();

    travelRecord.updateContent("새 제목", "새 본문", "https://cdn.example.com/new.jpg");

    assertThat(travelRecord.getTitle()).isEqualTo("새 제목");
    assertThat(travelRecord.getContent()).isEqualTo("새 본문");
    assertThat(travelRecord.getCoverImageUrl()).isEqualTo("https://cdn.example.com/new.jpg");
    assertThat(travelRecord.getOverallRating()).isEqualTo(4);
  }

  @Test
  @DisplayName("평점을 넘기면 평점까지 갱신한다")
  void updateContentWithRatingUpdatesRating() {
    TravelRecord travelRecord =
        TravelRecord.builder().author(author()).title("이전 제목").overallRating(4).build();

    travelRecord.updateContent(null, null, null, 5);

    assertThat(travelRecord.getTitle()).isEqualTo("이전 제목");
    assertThat(travelRecord.getOverallRating()).isEqualTo(5);
  }

  @Test
  @DisplayName("방문 여부를 지정하지 않은 기록 장소는 미방문으로 시작한다")
  void travelRecordPlaceDefaultsToNotVisited() {
    TravelRecordPlace place =
        TravelRecordPlace.builder()
            .travelRecordDay(new TravelRecordDay())
            .sequence(1)
            .placeName("광안리")
            .address("부산 수영구")
            .latitude(35.153)
            .longitude(129.118)
            .provider(PlaceProvider.GOOGLE)
            .providerPlaceId("google-place-id")
            .build();

    assertThat(place.getVisited()).isFalse();
  }

  @Test
  @DisplayName("장소 리뷰 수정은 null이 아닌 값만 반영한다")
  void placeReviewUpdateAppliesOnlyNonNullValues() {
    PlaceReview review =
        PlaceReview.builder()
            .author(author())
            .rating(3)
            .stayMinutes(30)
            .content("보통")
            .tags(List.of("조용함"))
            .build();

    review.update(5, 90, "아주 좋았다", List.of("야경", "가족"));

    assertThat(review.getRating()).isEqualTo(5);
    assertThat(review.getStayMinutes()).isEqualTo(90);
    assertThat(review.getContent()).isEqualTo("아주 좋았다");
    assertThat(review.getTags()).containsExactly("야경", "가족");

    review.update(null, null, null, null);

    assertThat(review.getRating()).isEqualTo(5);
    assertThat(review.getStayMinutes()).isEqualTo(90);
    assertThat(review.getContent()).isEqualTo("아주 좋았다");
    assertThat(review.getTags()).containsExactly("야경", "가족");
  }

  private User author() {
    return User.builder()
        .email("author@example.com")
        .provider("google")
        .providerId("google-author")
        .name(new Name("Kim", "Tester"))
        .nickname("author")
        .role(UserRole.USER)
        .build();
  }
}
