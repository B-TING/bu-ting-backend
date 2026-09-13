package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공정 로테이션 슬롯 배정 제안(FR-RND-03).
 *
 * <p>직전 2회차에 열리지 않은 구역을 우선하고, 동점이면 이번 주 오픈 횟수가 적은 구역을, 그래도 같으면 무작위로 고른다. 6구역이 자연히 돌아가도록 최근 오픈 구역을
 * 뒤로 민다.
 */
@Service
@RequiredArgsConstructor
public class RoundSlotSuggestionService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final Random random = new Random();

  @Transactional(readOnly = true)
  public SlotSuggestionResDto suggest(OffsetDateTime referenceTime, int authSlots) {
    Set<String> recentZones = zonesOfRounds(recentRounds(referenceTime));
    Map<String, Integer> weekOpenCount = openCounts(thisWeekRounds(referenceTime));

    List<String> zones = new ArrayList<>();
    for (ChatZone zone : ChatZone.values()) {
      zones.add(zone.name());
    }
    Collections.shuffle(zones, random); // 무작위 타이브레이크

    zones.sort(
        Comparator.<String>comparingInt(zone -> recentZones.contains(zone) ? 1 : 0)
            .thenComparingInt(zone -> weekOpenCount.getOrDefault(zone, 0)));

    int count = Math.min(Math.max(authSlots, 0), zones.size());
    List<String> chosen = new ArrayList<>(zones.subList(0, count));
    List<String> rationale = new ArrayList<>();
    for (String zone : chosen) {
      rationale.add(
          zone
              + ": "
              + (recentZones.contains(zone) ? "최근 오픈" : "직전 2회차 미오픈")
              + ", 이번 주 오픈 "
              + weekOpenCount.getOrDefault(zone, 0)
              + "회");
    }
    return new SlotSuggestionResDto(chosen, rationale);
  }

  private List<ZoneEventRound> recentRounds(OffsetDateTime referenceTime) {
    return roundRepository.findTop2ByStartsAtLessThanOrderByStartsAtDesc(referenceTime);
  }

  private List<ZoneEventRound> thisWeekRounds(OffsetDateTime referenceTime) {
    return roundRepository.findByStartsAtGreaterThanEqual(referenceTime.minusDays(7));
  }

  private Set<String> zonesOfRounds(List<ZoneEventRound> rounds) {
    Set<String> zones = new HashSet<>();
    for (ZoneEventRound round : rounds) {
      for (ZoneEventRoundSlot slot : slotRepository.findByRound_Id(round.getId())) {
        zones.add(slot.getZoneId());
      }
    }
    return zones;
  }

  private Map<String, Integer> openCounts(List<ZoneEventRound> rounds) {
    Map<String, Integer> counts = new HashMap<>();
    for (ZoneEventRound round : rounds) {
      for (ZoneEventRoundSlot slot : slotRepository.findByRound_Id(round.getId())) {
        counts.merge(slot.getZoneId(), 1, Integer::sum);
      }
    }
    return counts;
  }
}
