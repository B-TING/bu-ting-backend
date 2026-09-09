package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 종료(이벤트 CLOSED 전환) 시점의 좋아요 순위를 고정한다.
 *
 * <p>Top N 경계에서 동점이면 동점자 전원을 포함해 기록하고, 관리자가 {@code winners/confirm}으로 최종 선정할 때까지 자동으로 풀리지
 * 않는다({@link ZoneEventRankingSnapshot}). 이벤트에 우수 보상이 설정돼 있지 않거나 이미 스냅샷이 존재하면 아무 일도 하지 않는다.
 *
 * <p>신고 누적으로 자동 숨김된 참여도 순위 산정에 포함한다 — 제외하면 신고가 기각된 뒤에도 확정할 스냅샷 행이 없고 컷오프가 조용히 밀린다. 대신 미해결 신고가 있는
 * 동안은 {@code top-n}·{@code winners/confirm}이 신고 상태로 보류를 판단한다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventRankingSnapshotService {

  private static final int VERSION = 1;

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventRankingSnapshotRepository snapshotRepository;

  @Transactional
  public void freeze(ZoneEvent event, OffsetDateTime closedAt) {
    if (snapshotRepository.countByEventId(event.getId()) > 0) {
      return;
    }
    RewardSnapshot excellence = event.getExcellenceReward();
    if (excellence == null || excellence.topN() == null || excellence.topN() <= 0) {
      return;
    }
    List<ZoneEventParticipation> ranked =
        participationRepository.findRankedPublicSuccessByEvent(event.getId());
    if (ranked.isEmpty()) {
      return;
    }

    int cutoffIndex = Math.min(excellence.topN(), ranked.size()) - 1;
    long cutoffCount = ranked.get(cutoffIndex).getLikeCount();
    long tiedAtCutoff = ranked.stream().filter(p -> p.getLikeCount() == cutoffCount).count();

    int rank = 0;
    long previousCount = -1;
    for (int i = 0; i < ranked.size(); i++) {
      ZoneEventParticipation p = ranked.get(i);
      long count = p.getLikeCount();
      if (count < cutoffCount) {
        break; // 정렬돼 있으므로 이후는 전부 컷오프 미만
      }
      if (count != previousCount) {
        rank = i + 1;
        previousCount = count;
      }
      boolean tied = count == cutoffCount && tiedAtCutoff > 1;
      snapshotRepository.save(
          ZoneEventRankingSnapshot.builder()
              .eventId(event.getId())
              .closedAt(closedAt)
              .version(VERSION)
              .participationId(p.getId())
              .rankN(rank)
              .likeCountAtClose(count)
              .tied(tied)
              .build());
    }
  }
}
