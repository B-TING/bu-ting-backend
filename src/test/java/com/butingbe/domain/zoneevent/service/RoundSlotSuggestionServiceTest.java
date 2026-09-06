package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RoundSlotSuggestionServiceTest extends AbstractContainerTest {

  @Autowired private RoundSlotSuggestionService suggestionService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;

  @Test
  @DisplayName("직전 회차에 오픈된 구역은 제안에서 뒤로 밀린다")
  void deprioritisesRecentlyOpenedZones() {
    // 직전 회차에서 이 두 구역이 열렸다.
    ZoneEventRound past = savedRound(-1);
    savedSlot(past, "SUYEONG_NAMGU");
    savedSlot(past, "YEONGDO");

    SlotSuggestionResDto suggestion = suggestionService.suggest(OffsetDateTime.now(), 4);

    assertThat(suggestion.slots()).hasSize(4);
    // 최근 오픈 두 구역은 우선순위가 낮아 앞쪽 4개에 최소화된다: 미오픈 4구역이 먼저 채워진다.
    assertThat(suggestion.slots()).doesNotContain("SUYEONG_NAMGU", "YEONGDO");
    assertThat(suggestion.rationale()).hasSize(4);
  }

  @Test
  @DisplayName("과거 회차가 없으면 6구역 중 요청 수만큼 제안한다")
  void suggestsWithoutHistory() {
    SlotSuggestionResDto suggestion = suggestionService.suggest(OffsetDateTime.now(), 4);

    assertThat(suggestion.slots()).hasSize(4);
    assertThat(suggestion.slots()).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("요청 슬롯 수가 6을 넘거나 음수여도 안전하게 처리한다")
  void clampsRequestedCount() {
    assertThat(suggestionService.suggest(OffsetDateTime.now(), 99).slots()).hasSize(6);
    assertThat(suggestionService.suggest(OffsetDateTime.now(), -1).slots()).isEmpty();
  }

  private ZoneEventRound savedRound(int startsDaysOffset) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .startsAt(OffsetDateTime.now().plusDays(startsDaysOffset))
            .endsAt(OffsetDateTime.now().plusDays(startsDaysOffset + 1))
            .status(RoundStatus.CLOSED)
            .build());
  }

  private void savedSlot(ZoneEventRound round, String zoneId) {
    slotRepository.save(
        com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId(zoneId)
            .build());
  }
}
