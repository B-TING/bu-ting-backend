package com.butingbe.domain.place.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.dto.response.PlaceCandidateResDto;
import java.util.Collection;
import java.util.List;

/**
 * 일정에 채워 넣을 후보 장소를 고른다.
 *
 * <p>사용자가 장소를 거의 고르지 않아도 일정이 나오게 하려면 서버가 나머지를 채워야 한다. 호출자가 장소 도메인의 저장소를 직접 다루지 않도록 조회 경로를 좁힌다.
 */
public interface PlaceCandidateFinder {

  /**
   * 인기순으로 후보를 고른다.
   *
   * @param zones 제한할 권역. 비어 있으면 부산 전체에서 고른다.
   * @param excludedProviderPlaceIds 이미 일정에 들어간 장소. 중복으로 추천하지 않는다.
   * @param limit 최대 개수
   */
  List<PlaceCandidateResDto> findCandidates(
      Collection<ChatZone> zones, Collection<String> excludedProviderPlaceIds, int limit);
}
