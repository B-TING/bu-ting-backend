package com.butingbe.domain.reward.service;

import com.butingbe.domain.reward.dto.response.SettlementReportResDto;
import com.butingbe.domain.reward.dto.response.SettlementReportResDto.EventPrizes;
import com.butingbe.domain.reward.dto.response.SettlementReportResDto.Prize;
import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.UserCoupon;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 우수 인증(TOP_LIKE) 정산.
 *
 * <p>회차 행을 잠그고(BR-12), 각 이벤트의 공개 성공 참여를 좋아요순으로 정렬해 상위 N명에게 우수 보상(쿠폰/기프티콘)을 지급한다. 재고나 월 캡이 부족하면 그
 * 지급을 건너뛰고 리포트에 남긴다. 지급은 참여·사유·보상 UK로 멱등이라, 재실행해도 같은 결과가 된다.
 */
@Service
@RequiredArgsConstructor
public class RewardSettlementService {

  private static final String STATUS_GRANTED = "GRANTED";
  private static final String STATUS_OUT_OF_STOCK = "SKIPPED_OUT_OF_STOCK";
  private static final String STATUS_MONTHLY_CAP = "SKIPPED_MONTHLY_CAP";
  private static final String STATUS_ALREADY = "ALREADY_GRANTED";

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final RewardCatalogRepository rewardCatalogRepository;
  private final RewardGrantRepository rewardGrantRepository;
  private final UserCouponRepository userCouponRepository;

  @Transactional
  public SettlementReportResDto settleTopLike(UUID roundId) {
    roundRepository
        .findWithLockById(roundId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));

    List<EventPrizes> eventPrizes = new ArrayList<>();
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      RewardSnapshot excellence = event.getExcellenceReward();
      RewardCatalog prize = eligiblePrize(excellence);
      if (prize == null) {
        continue;
      }
      List<Prize> prizes = new ArrayList<>();
      List<ZoneEventParticipation> winners =
          participationRepository.findTopPublicSuccessByEvent(
              event.getId(), PageRequest.of(0, excellence.topN()));
      for (ZoneEventParticipation winner : winners) {
        prizes.add(grantPrize(winner, event.getId(), prize));
      }
      eventPrizes.add(new EventPrizes(event.getId().toString(), prizes));
    }
    return new SettlementReportResDto(roundId.toString(), eventPrizes);
  }

  /** 우수 보상이 유효하면 카탈로그를 돌려주고, 아니면 null(정산 제외). */
  private RewardCatalog eligiblePrize(RewardSnapshot excellence) {
    if (excellence == null
        || excellence.topN() == null
        || excellence.topN() <= 0
        || excellence.prizeRewardCode() == null) {
      return null;
    }
    return rewardCatalogRepository.findByCode(excellence.prizeRewardCode()).orElse(null);
  }

  private Prize grantPrize(ZoneEventParticipation winner, UUID eventId, RewardCatalog prize) {
    UUID userId = winner.getUserId();
    if (rewardGrantRepository.existsByParticipationIdAndGrantReasonAndReward_Id(
        winner.getId(), GrantReason.TOP_LIKE, prize.getId())) {
      return prize(userId, winner.getId(), prize.getCode(), STATUS_ALREADY);
    }
    if (prize.getStock() != null && prize.getStock() <= 0) {
      return prize(userId, winner.getId(), prize.getCode(), STATUS_OUT_OF_STOCK);
    }
    if (prize.getMonthlyCap() != null
        && rewardGrantRepository.countByReward_IdAndGrantReasonAndGrantedAtGreaterThanEqual(
                prize.getId(), GrantReason.TOP_LIKE, monthStart())
            >= prize.getMonthlyCap()) {
      return prize(userId, winner.getId(), prize.getCode(), STATUS_MONTHLY_CAP);
    }

    RewardGrant grant =
        rewardGrantRepository.save(
            RewardGrant.builder()
                .userId(userId)
                .reward(prize)
                .participationId(winner.getId())
                .eventId(eventId)
                .grantReason(GrantReason.TOP_LIKE)
                .grantedAt(OffsetDateTime.now())
                .build());
    if (prize.getStock() != null) {
      prize.update(null, prize.getStock() - 1, null, null);
    }
    userCouponRepository.save(
        UserCoupon.builder()
            .userId(userId)
            .reward(prize)
            .grantId(grant.getId())
            .expiresAt(
                prize.getValidDays() == null
                    ? null
                    : OffsetDateTime.now().plusDays(prize.getValidDays()))
            .build());
    return prize(userId, winner.getId(), prize.getCode(), STATUS_GRANTED);
  }

  private Prize prize(UUID userId, UUID participationId, String code, String status) {
    return new Prize(userId.toString(), participationId.toString(), code, status);
  }

  private OffsetDateTime monthStart() {
    return OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
        .with(TemporalAdjusters.firstDayOfMonth())
        .toLocalDate()
        .atStartOfDay(ZoneId.of("Asia/Seoul"))
        .toOffsetDateTime();
  }
}
