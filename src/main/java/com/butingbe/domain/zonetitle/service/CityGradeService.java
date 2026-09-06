package com.butingbe.domain.zonetitle.service;

import com.butingbe.domain.zonetitle.dto.response.CityGradeResDto;
import com.butingbe.domain.zonetitle.entity.CityGrade;
import com.butingbe.domain.zonetitle.entity.UserCityGradeHistory;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import com.butingbe.domain.zonetitle.repository.UserCityGradeHistoryRepository;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도시 등급 계산과 상승 이력.
 *
 * <p>등급은 저장하지 않고 구역 칭호 보유 현황으로 매번 계산한다. 등급이 이전보다 오르면 그 시점만 이력에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class CityGradeService {

  private final UserZoneTitleRepository userZoneTitleRepository;
  private final UserCityGradeHistoryRepository historyRepository;

  /** 현재 등급을 계산한다. */
  @Transactional(readOnly = true)
  public CityGrade computeGrade(UUID userId) {
    return CityGrade.from(maxTierByZone(userId));
  }

  /** 등급이 직전 이력보다 올랐으면 새 이력을 남긴다(발급 직후 호출). */
  @Transactional
  public void recordIfRisen(UUID userId) {
    CityGrade current = computeGrade(userId);
    CityGrade last =
        historyRepository
            .findTopByUserIdOrderByReachedAtDesc(userId)
            .map(UserCityGradeHistory::getGrade)
            .orElse(null);
    if (current != CityGrade.BEGINNER && (last == null || current.ordinal() > last.ordinal())) {
      historyRepository.save(UserCityGradeHistory.builder().userId(userId).grade(current).build());
    }
  }

  /** 등급 + 최근 상승 시점 + 다음 등급 조건. */
  @Transactional(readOnly = true)
  public CityGradeResDto cityGradeOf(UUID userId) {
    CityGrade grade = computeGrade(userId);
    OffsetDateTime reachedAt =
        historyRepository
            .findTopByUserIdOrderByReachedAtDesc(userId)
            .filter(history -> history.getGrade() == grade)
            .map(UserCityGradeHistory::getReachedAt)
            .orElse(null);
    return CityGradeResDto.of(grade, reachedAt);
  }

  private Map<String, Integer> maxTierByZone(UUID userId) {
    Map<String, Integer> maxTier = new HashMap<>();
    for (UserZoneTitle title : userZoneTitleRepository.findByUserIdAndRevokedAtIsNull(userId)) {
      maxTier.merge(title.getZoneId(), title.getTitleDef().getTier(), Integer::max);
    }
    return maxTier;
  }
}
