package com.butingbe.domain.zonetitle.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import com.butingbe.domain.zonetitle.dto.response.MyZoneTitlesResDto;
import com.butingbe.domain.zonetitle.dto.response.MyZoneTitlesResDto.TitleItem;
import com.butingbe.domain.zonetitle.dto.response.MyZoneTitlesResDto.ZoneProgress;
import com.butingbe.domain.zonetitle.dto.response.ZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구역 칭호 발급·장착·진행도.
 *
 * <p>성공 확정 시 그 구역의 누적 성공 수(REVOKED 제외)로 도달한 tier의 미보유 칭호를 발급한다. 발급은 멱등이다. 대표 칭호는 유저당 하나이며, 처음 칭호를
 * 얻을 때만 자동 장착하고 이후에는 유저가 수동으로 바꾼다(OQ-07).
 */
@Service
@RequiredArgsConstructor
public class ZoneTitleService {

  private final ZoneTitleDefRepository titleDefRepository;
  private final UserZoneTitleRepository userZoneTitleRepository;
  private final ZoneEventParticipationRepository participationRepository;

  /** 구역 성공 누적으로 새로 도달한 칭호를 발급하고, 새로 얻은 칭호 목록을 돌려준다. */
  @Transactional
  public List<EquippedTitleResDto> awardTitles(UUID userId, String zoneId) {
    long successCount = participationRepository.countSuccessByUserAndZone(userId, zoneId);
    boolean autoEquip = userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId) == 0;

    List<UserZoneTitle> newlyEarned = new ArrayList<>();
    for (ZoneTitleDef def : titleDefRepository.findByZoneIdOrderByTierAsc(zoneId)) {
      if (def.getRequiredSuccessCount() <= successCount
          && !userZoneTitleRepository.existsByUserIdAndTitleDef_Id(userId, def.getId())) {
        newlyEarned.add(
            userZoneTitleRepository.save(
                UserZoneTitle.builder()
                    .userId(userId)
                    .titleDef(def)
                    .zoneId(zoneId)
                    .equipped(false)
                    .build()));
      }
    }
    if (autoEquip && !newlyEarned.isEmpty()) {
      // tier 오름차순으로 발급했으므로 마지막이 가장 높은 tier.
      newlyEarned.get(newlyEarned.size() - 1).equip();
    }
    return newlyEarned.stream().map(EquippedTitleResDto::from).toList();
  }

  /** 대표 칭호를 장착한다. 보유하지 않은 칭호면 403. */
  @Transactional
  public EquippedTitleResDto equip(AuthenticatedUser user, UUID userTitleId) {
    UUID userId = requireUserId(user);
    UserZoneTitle title =
        userZoneTitleRepository
            .findById(userTitleId)
            .filter(t -> t.getUserId().equals(userId) && t.getRevokedAt() == null)
            .orElseThrow(() -> new ForbiddenException("error.zone_title.not_owned"));
    userZoneTitleRepository.findByUserIdAndEquippedIsTrue(userId).ifPresent(UserZoneTitle::unequip);
    title.equip();
    return EquippedTitleResDto.from(title);
  }

  /** 대표 칭호를 해제한다. */
  @Transactional
  public void unequip(AuthenticatedUser user) {
    UUID userId = requireUserId(user);
    userZoneTitleRepository.findByUserIdAndEquippedIsTrue(userId).ifPresent(UserZoneTitle::unequip);
  }

  /** 내 칭호 보유·진행도. */
  @Transactional(readOnly = true)
  public MyZoneTitlesResDto myTitles(AuthenticatedUser user) {
    UUID userId = requireUserId(user);
    List<UserZoneTitle> owned = userZoneTitleRepository.findByUserIdAndRevokedAtIsNull(userId);
    Map<String, List<UserZoneTitle>> byZone =
        owned.stream().collect(Collectors.groupingBy(UserZoneTitle::getZoneId));

    EquippedTitleResDto equipped =
        owned.stream()
            .filter(t -> Boolean.TRUE.equals(t.getEquipped()))
            .findFirst()
            .map(EquippedTitleResDto::from)
            .orElse(null);

    List<ZoneProgress> zones = new ArrayList<>();
    for (ChatZone zone : ChatZone.values()) {
      String zoneId = zone.name();
      long successCount = participationRepository.countSuccessByUserAndZone(userId, zoneId);
      List<ZoneTitleDef> defs = titleDefRepository.findByZoneIdOrderByTierAsc(zoneId);
      List<UserZoneTitle> zoneTitles = byZone.getOrDefault(zoneId, List.of());
      int currentTier =
          zoneTitles.stream().mapToInt(t -> t.getTitleDef().getTier()).max().orElse(0);
      Integer nextTier = null;
      Integer remaining = null;
      for (ZoneTitleDef def : defs) {
        if (def.getTier() > currentTier) {
          nextTier = def.getTier();
          remaining = Math.max(0, (int) (def.getRequiredSuccessCount() - successCount));
          break;
        }
      }
      List<TitleItem> items =
          zoneTitles.stream()
              .sorted((a, b) -> a.getTitleDef().getTier() - b.getTitleDef().getTier())
              .map(
                  t ->
                      new TitleItem(
                          t.getId().toString(),
                          t.getTitleDef().getTitleCode(),
                          t.getTitleDef().getTitleName(),
                          t.getTitleDef().getTier(),
                          t.getTitleDef().getStyle(),
                          t.getTitleDef().getColor(),
                          t.getEarnedAt(),
                          Boolean.TRUE.equals(t.getEquipped())))
              .toList();
      zones.add(new ZoneProgress(zoneId, successCount, currentTier, nextTier, remaining, items));
    }
    return new MyZoneTitlesResDto(equipped, null, zones);
  }

  /** 칭호 정의 전체(18개). */
  @Transactional(readOnly = true)
  public List<ZoneTitleDefResDto> allDefs() {
    return titleDefRepository.findAllByOrderByZoneIdAscTierAsc().stream()
        .map(ZoneTitleDefResDto::from)
        .toList();
  }

  /** 여러 유저의 장착 칭호를 한 번에 조회한다(앨범·댓글용). */
  @Transactional(readOnly = true)
  public Map<UUID, EquippedTitleResDto> equippedTitlesByUsers(Collection<UUID> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return userZoneTitleRepository.findByUserIdInAndEquippedIsTrue(userIds).stream()
        .collect(
            Collectors.toMap(UserZoneTitle::getUserId, EquippedTitleResDto::from, (a, b) -> a));
  }

  private UUID requireUserId(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    return user.id();
  }
}
