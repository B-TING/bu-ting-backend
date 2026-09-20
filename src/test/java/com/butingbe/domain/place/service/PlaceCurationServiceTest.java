package com.butingbe.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.place.dto.request.PlaceCurationReqDto;
import com.butingbe.domain.place.dto.response.PlaceCurationResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.entity.PlaceTimeSlot;
import com.butingbe.domain.place.repository.PlaceRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class PlaceCurationServiceTest {

  private static final UUID PLACE_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final AuthenticatedUser ADMIN =
      new AuthenticatedUser(
          UUID.randomUUID(),
          "admin@example.com",
          "admin",
          List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

  @Mock private PlaceRepository placeRepository;
  @Mock private OperatorAuthorization operatorAuthorization;
  @InjectMocks private PlaceCurationService placeCurationService;

  @Test
  @DisplayName("체류 시간과 시간대를 보정한다")
  void curatesBoth() {
    Place place = place(90, null);
    when(placeRepository.findById(PLACE_ID)).thenReturn(Optional.of(place));

    PlaceCurationResDto result =
        placeCurationService.curate(
            ADMIN, PLACE_ID, new PlaceCurationReqDto(120, PlaceTimeSlot.EVENING));

    assertThat(place.getDwellMinutes()).isEqualTo(120);
    assertThat(place.getPreferredTimeSlot()).isEqualTo(PlaceTimeSlot.EVENING);
    assertThat(result.dwellMinutes()).isEqualTo(120);
    assertThat(result.preferredTimeSlot()).isEqualTo(PlaceTimeSlot.EVENING);
  }

  @Test
  @DisplayName("null 필드는 기존 값을 유지한다")
  void keepsExistingValuesForNullFields() {
    Place place = place(90, PlaceTimeSlot.MORNING);
    when(placeRepository.findById(PLACE_ID)).thenReturn(Optional.of(place));

    placeCurationService.curate(ADMIN, PLACE_ID, new PlaceCurationReqDto(null, null));

    assertThat(place.getDwellMinutes()).isEqualTo(90);
    assertThat(place.getPreferredTimeSlot()).isEqualTo(PlaceTimeSlot.MORNING);
  }

  @Test
  @DisplayName("없는 장소는 404다")
  void rejectsUnknownPlace() {
    when(placeRepository.findById(PLACE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> placeCurationService.curate(ADMIN, PLACE_ID, new PlaceCurationReqDto(120, null)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("운영자가 아니면 보정하지 않는다")
  void rejectsNonOperator() {
    doThrow(new ForbiddenException("error.operator.forbidden"))
        .when(operatorAuthorization)
        .requireOperator(any());

    assertThatThrownBy(
            () -> placeCurationService.curate(ADMIN, PLACE_ID, new PlaceCurationReqDto(120, null)))
        .isInstanceOf(ForbiddenException.class);
    verify(placeRepository, never()).findById(any());
  }

  private Place place(Integer dwellMinutes, PlaceTimeSlot timeSlot) {
    return Place.builder()
        .provider("TOUR_API")
        .providerPlaceId("126508")
        .name("감천문화마을")
        .contentTypeId("12")
        .dwellMinutes(dwellMinutes)
        .preferredTimeSlot(timeSlot)
        .build();
  }
}
