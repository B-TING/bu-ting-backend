package com.butingbe.domain.place.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.place.dto.request.PlaceCurationReqDto;
import com.butingbe.domain.place.dto.response.PlaceCurationResDto;
import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소의 체류 시간과 시간대를 운영이 보정한다.
 *
 * <p>유형별 기본값은 동기화가 채우지만, 인기 장소의 실측 체류 시간이나 야경·일몰처럼 시간대가 중요한 장소는 사람이 정해야 한다. 상위 장소가 일정의 대부분을 차지하므로
 * 전체를 수기로 채우지 않고 필요한 곳만 손본다.
 */
@Service
@RequiredArgsConstructor
public class PlaceCurationService {

  private final PlaceRepository placeRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional
  public PlaceCurationResDto curate(
      AuthenticatedUser user, UUID placeId, PlaceCurationReqDto request) {
    operatorAuthorization.requireOperator(user);

    Place place =
        placeRepository
            .findById(placeId)
            .orElseThrow(() -> new ResourceNotFoundException("error.place.not_found"));
    place.applyCuration(request.dwellMinutes(), request.preferredTimeSlot());
    return PlaceCurationResDto.from(place);
  }
}
