package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelServiceImpl implements TravelService {

  private final TravelRepository travelRepository;

  @Override
  @Transactional
  public TravelResDto createTravel(AuthenticatedUser authenticatedUser, TravelCreateReqDto request) {
    validateAuthenticatedUser(authenticatedUser);
    validateTravelDate(request);

    Travel travel =
        Travel.builder()
            .title(request.title())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .status(TravelStatus.PLANNED)
            .hasHeavyBaggage(request.hasHeavyBaggage())
            .hasPets(request.hasPets())
            .travelStyle(request.travelStyle())
            .preferFlatTerrain(request.preferFlatTerrain())
            .pace(request.pace())
            .companionCount(request.companionCount())
            .preferredFoods(request.preferredFoods())
            .companionTypes(request.companionTypes())
            .accommodationArea(request.accommodationArea())
            .build();

    return TravelResDto.from(travelRepository.save(travel));
  }

  private void validateAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null) {
      throw new UnauthenticatedException();
    }

    if (authenticatedUser.id() == null && !authenticatedUser.isDevelopmentAdmin()) {
      throw new UnauthenticatedException();
    }
  }

  private void validateTravelDate(TravelCreateReqDto request) {
    if (request.endDate().isBefore(request.startDate())) {
      throw new IllegalArgumentException("여행 종료 날짜는 시작 날짜보다 빠를 수 없습니다.");
    }
  }
}
