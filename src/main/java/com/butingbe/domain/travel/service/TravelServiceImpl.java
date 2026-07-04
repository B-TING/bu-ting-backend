package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelServiceImpl implements TravelService {

  private final TravelRepository travelRepository;
  private final TravelMemberRepository travelMemberRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public TravelResDto createTravel(AuthenticatedUser authenticatedUser, TravelCreateReqDto request) {
    User user = findAuthenticatedUser(authenticatedUser);
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

    Travel savedTravel = travelRepository.save(travel);
    travelMemberRepository.save(
        TravelMember.builder().travel(savedTravel).user(user).role(TravelTeamRole.LEADER).build());

    return TravelResDto.from(savedTravel);
  }

  private User findAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    return userRepository
        .findById(authenticatedUser.id())
        .orElseThrow(UnauthenticatedException::new);
  }

  private void validateTravelDate(TravelCreateReqDto request) {
    if (request.endDate().isBefore(request.startDate())) {
      throw new IllegalArgumentException("Travel end date cannot be before start date.");
    }
  }
}
