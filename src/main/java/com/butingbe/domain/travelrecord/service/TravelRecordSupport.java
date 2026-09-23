package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.InvalidRequestException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 여행기 도메인의 여러 서비스가 함께 쓰는 확인 절차.
 *
 * <p>{@code TravelRecordServiceImpl} 을 유스케이스별로 쪼개면서 생겼다. 세 메서드뿐이지만 복사해 두면 한쪽만 고쳐져 갈라진다. 남은 분할
 * (피드·소셜·복제)도 같은 것을 필요로 한다.
 */
@Component
@RequiredArgsConstructor
public class TravelRecordSupport {

  private final UserRepository userRepository;
  private final TravelMemberRepository travelMemberRepository;

  /** 인증 주체를 실제 사용자로 바꾼다. id 없는 개발 관리자 토큰도 여기서 걸린다. */
  public User findAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    return userRepository
        .findById(authenticatedUser.id())
        .orElseThrow(UnauthenticatedException::new);
  }

  public void validatePlaceId(String placeId) {
    if (placeId == null || placeId.isBlank()) {
      throw new InvalidRequestException("error.place.id_required");
    }
  }

  public void validateTravelMember(UUID travelId, UUID userId) {
    if (!travelMemberRepository.existsByTravel_IdAndUser_Id(travelId, userId)) {
      throw new ForbiddenException("error.travel.not_member");
    }
  }
}
