package com.butingbe.domain.travelrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.PlanRouteRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCloneToTravelReqDto;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.PlaceReviewImage;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewImageRepository;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordBookmarkRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordCommentRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordLikeRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordPlaceRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRouteRepository;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** 통합 테스트로는 만들 수 없는 상태 - 제목이 빈 기록, 일정이 없는 발행 기록, 외부 URL만 가진 리뷰 이미지 - 를 저장소 목으로 직접 구성해 검증한다. */
@ExtendWith(MockitoExtension.class)
class TravelRecordServiceImplMockTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID RECORD_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID REVIEW_ID = UUID.fromString("66666666-0000-0000-0000-000000000001");

  @Mock private TravelRepository travelRepository;
  @Mock private PlanRepository planRepository;
  @Mock private PlanPlaceRepository planPlaceRepository;
  @Mock private PlanRouteRepository planRouteRepository;
  @Mock private TravelMemberRepository travelMemberRepository;
  @Mock private UserRepository userRepository;
  @Mock private TravelRecordRepository travelRecordRepository;
  @Mock private TravelRecordDayRepository travelRecordDayRepository;
  @Mock private TravelRecordPlaceRepository travelRecordPlaceRepository;
  @Mock private TravelRecordRouteRepository travelRecordRouteRepository;
  @Mock private FileStorageService fileStorageService;
  @Mock private PlaceReviewRepository placeReviewRepository;
  @Mock private PlaceReviewImageRepository placeReviewImageRepository;
  @Mock private TravelRecordBookmarkRepository travelRecordBookmarkRepository;
  @Mock private TravelRecordLikeRepository travelRecordLikeRepository;
  @Mock private TravelRecordCommentRepository travelRecordCommentRepository;

  @InjectMocks private TravelRecordServiceImpl travelRecordService;

  private User author;
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    author = user();
    authenticatedUser = new AuthenticatedUser(USER_ID, "author@example.com", "author", List.of());
  }

  @Test
  @DisplayName("제목이 비어 있는 초안은 발행할 수 없다")
  void rejectsPublishWhenTitleIsBlank() {
    TravelRecord record = record(TravelRecordStatus.DRAFT, "   ");

    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
    when(travelRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

    assertThatThrownBy(() -> travelRecordService.publish(authenticatedUser, TRAVEL_ID, RECORD_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record title is required.");
  }

  @Test
  @DisplayName("일정이 없는 발행 기록은 내 여행으로 복제할 수 없다")
  void rejectsCloneWhenItineraryIsEmpty() {
    TravelRecord record = record(TravelRecordStatus.PUBLISHED, "부산 3일");

    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
    when(travelRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
    when(travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(RECORD_ID))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                travelRecordService.cloneToTravel(
                    authenticatedUser,
                    RECORD_ID,
                    new TravelRecordCloneToTravelReqDto(
                        "복제",
                        LocalDate.of(2026, 10, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record itinerary is required.");

    verify(travelRepository, never()).save(any(Travel.class));
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

    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
    when(travelMemberRepository.existsByTravel_IdAndUser_Id(TRAVEL_ID, USER_ID)).thenReturn(true);
    when(planPlaceRepository.findById(any())).thenReturn(Optional.of(planPlace()));
    when(placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(any(), any()))
        .thenReturn(Optional.of(review));
    when(placeReviewImageRepository.findByPlaceReview_IdOrderBySequenceAsc(REVIEW_ID))
        .thenReturn(List.of(externalImage));

    var response =
        travelRecordService.getPlaceReview(authenticatedUser, TRAVEL_ID, UUID.randomUUID());

    assertThat(response.mediaUrls()).containsExactly("https://legacy.example.com/photo.jpg");
    verify(fileStorageService, never()).getPresignedUrl(any());
  }

  private TravelRecord record(TravelRecordStatus status, String title) {
    TravelRecord record =
        TravelRecord.builder()
            .author(author)
            .originalTravel(travel())
            .title(title)
            .status(status)
            .publishedAt(status == TravelRecordStatus.PUBLISHED ? LocalDateTime.now() : null)
            .build();
    ReflectionTestUtils.setField(record, "id", RECORD_ID);
    return record;
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

  private com.butingbe.domain.travel.entity.PlanPlace planPlace() {
    var plan =
        com.butingbe.domain.travel.entity.Plan.builder()
            .travel(travel())
            .dayNumber(1)
            .visitDate(LocalDate.of(2026, 9, 1))
            .build();
    var place =
        com.butingbe.domain.travel.entity.PlanPlace.builder()
            .plan(plan)
            .sequence(1)
            .placeName("광안리")
            .address("부산 수영구")
            .latitude(35.153)
            .longitude(129.118)
            .provider(com.butingbe.domain.travel.entity.PlaceProvider.GOOGLE)
            .providerPlaceId("google-place-id")
            .build();
    return place;
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

  @Test
  @DisplayName("복사되지 않은 장소를 가리키는 경로는 기록 스냅샷에서 건너뛴다")
  void skipsRoutesPointingOutsideTheCopiedPlaces() {
    var travel = completedTravel();
    var plan =
        com.butingbe.domain.travel.entity.Plan.builder()
            .travel(travel)
            .dayNumber(1)
            .visitDate(LocalDate.of(2026, 9, 1))
            .build();
    ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());

    var copiedPlace = planPlaceWithId(plan, UUID.randomUUID(), "복사되는 장소");
    var removedPlace = planPlaceWithId(plan, UUID.randomUUID(), "삭제된 장소");
    var danglingRoute =
        com.butingbe.domain.travel.entity.PlanRoute.builder()
            .plan(plan)
            .fromPlace(copiedPlace)
            .toPlace(removedPlace)
            .transportType(com.butingbe.domain.travel.entity.TransportType.PUBLIC_TRANSPORT)
            .durationMinutes(20)
            .distanceMeters(5000)
            .provider(com.butingbe.domain.travel.entity.PlaceProvider.GOOGLE)
            .build();

    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
    when(travelMemberRepository.existsByTravel_IdAndUser_Id(TRAVEL_ID, USER_ID)).thenReturn(true);
    when(travelRepository.findById(TRAVEL_ID)).thenReturn(Optional.of(travel));
    when(travelRecordRepository.existsByOriginalTravel_IdAndAuthor_Id(TRAVEL_ID, USER_ID))
        .thenReturn(false);
    when(travelRecordRepository.save(any(TravelRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(planRepository.findByTravel_IdOrderByDayNumberAsc(TRAVEL_ID)).thenReturn(List.of(plan));
    when(travelRecordDayRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.getId()))
        .thenReturn(List.of(copiedPlace));
    when(travelRecordPlaceRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(any(), any()))
        .thenReturn(Optional.empty());
    when(planRouteRepository.findByPlan_Id(plan.getId())).thenReturn(List.of(danglingRoute));
    when(travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(any()))
        .thenReturn(List.of());

    travelRecordService.createDraft(authenticatedUser, TRAVEL_ID, null);

    verify(travelRecordRouteRepository, never()).save(any());
  }

  @Test
  @DisplayName("기록과 요청 모두 제목이 비어 있으면 기본 제목으로 복제한다")
  void cloneFallsBackToDefaultTitleWhenBothTitlesAreBlank() {
    TravelRecord record = record(TravelRecordStatus.PUBLISHED, "   ");
    var recordDay =
        com.butingbe.domain.travelrecord.entity.TravelRecordDay.builder()
            .travelRecord(record)
            .dayNumber(1)
            .visitDate(LocalDate.of(2026, 9, 1))
            .build();
    ReflectionTestUtils.setField(recordDay, "id", UUID.randomUUID());

    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
    when(travelRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
    when(travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(RECORD_ID))
        .thenReturn(List.of(recordDay));
    when(travelRepository.save(any(Travel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(travelRecordPlaceRepository.findByTravelRecordDay_IdOrderBySequenceAsc(recordDay.getId()))
        .thenReturn(List.of());
    when(travelRecordRouteRepository.findByTravelRecordDay_Id(recordDay.getId()))
        .thenReturn(List.of());
    when(planRepository.findByTravel_IdOrderByDayNumberAsc(any())).thenReturn(List.of());

    var response =
        travelRecordService.cloneToTravel(
            authenticatedUser,
            RECORD_ID,
            new TravelRecordCloneToTravelReqDto(
                null,
                LocalDate.of(2026, 10, 1),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(response.title()).isEqualTo("여행 기록");
  }

  @Test
  @DisplayName("복제 대상에 없는 장소를 가리키는 기록 경로는 건너뛴다")
  void skipsRecordRoutesPointingOutsideTheClonedPlaces() {
    TravelRecord record = record(TravelRecordStatus.PUBLISHED, "부산 3일");
    var recordDay =
        com.butingbe.domain.travelrecord.entity.TravelRecordDay.builder()
            .travelRecord(record)
            .dayNumber(1)
            .visitDate(LocalDate.of(2026, 9, 1))
            .build();
    ReflectionTestUtils.setField(recordDay, "id", UUID.randomUUID());

    var clonedPlace = recordPlace(recordDay, UUID.randomUUID(), "복제되는 장소");
    var removedPlace = recordPlace(recordDay, UUID.randomUUID(), "삭제된 장소");
    var danglingRoute =
        com.butingbe.domain.travelrecord.entity.TravelRecordRoute.builder()
            .travelRecordDay(recordDay)
            .fromPlace(clonedPlace)
            .toPlace(removedPlace)
            .transportType(com.butingbe.domain.travel.entity.TransportType.PUBLIC_TRANSPORT)
            .durationMinutes(20)
            .distanceMeters(5000)
            .provider(com.butingbe.domain.travel.entity.PlaceProvider.GOOGLE)
            .build();

    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(author));
    when(travelRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));
    when(travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(RECORD_ID))
        .thenReturn(List.of(recordDay));
    when(travelRepository.save(any(Travel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(travelRecordPlaceRepository.findByTravelRecordDay_IdOrderBySequenceAsc(recordDay.getId()))
        .thenReturn(List.of(clonedPlace));
    when(planPlaceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(travelRecordRouteRepository.findByTravelRecordDay_Id(recordDay.getId()))
        .thenReturn(List.of(danglingRoute));
    when(planRepository.findByTravel_IdOrderByDayNumberAsc(any())).thenReturn(List.of());

    travelRecordService.cloneToTravel(
        authenticatedUser,
        RECORD_ID,
        new TravelRecordCloneToTravelReqDto(
            "복제", LocalDate.of(2026, 10, 1), null, null, null, null, null, null, null, null, null));

    verify(planRouteRepository, never()).save(any());
  }

  private com.butingbe.domain.travelrecord.entity.TravelRecordPlace recordPlace(
      com.butingbe.domain.travelrecord.entity.TravelRecordDay recordDay, UUID id, String name) {
    var place =
        com.butingbe.domain.travelrecord.entity.TravelRecordPlace.builder()
            .travelRecordDay(recordDay)
            .sequence(1)
            .placeName(name)
            .address("부산")
            .latitude(35.1)
            .longitude(129.1)
            .provider(com.butingbe.domain.travel.entity.PlaceProvider.GOOGLE)
            .providerPlaceId(name)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private Travel completedTravel() {
    Travel travel =
        Travel.builder()
            .title("부산")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2026, 9, 3))
            .status(com.butingbe.domain.travel.entity.TravelStatus.COMPLETED)
            .build();
    ReflectionTestUtils.setField(travel, "id", TRAVEL_ID);
    return travel;
  }

  private com.butingbe.domain.travel.entity.PlanPlace planPlaceWithId(
      com.butingbe.domain.travel.entity.Plan plan, UUID id, String name) {
    var place =
        com.butingbe.domain.travel.entity.PlanPlace.builder()
            .plan(plan)
            .sequence(1)
            .placeName(name)
            .address("부산")
            .latitude(35.1)
            .longitude(129.1)
            .provider(com.butingbe.domain.travel.entity.PlaceProvider.GOOGLE)
            .providerPlaceId(name)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }
}
