package com.butingbe.domain.zonetitle.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleCreateReqDto;
import com.butingbe.domain.zonetitle.dto.request.AdminZoneTitleUpdateReqDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderItemResDto;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderPageResDto;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구역 칭호 정의 관리(CRUD)와 보유자 조회. ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminZoneTitleService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneTitleDefRepository titleDefRepository;
  private final UserZoneTitleRepository userZoneTitleRepository;
  private final ZoneTitleService zoneTitleService;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final OperatorAuthorization operatorAuthorization;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<AdminZoneTitleDefResDto> list(AuthenticatedUser user) {
    operatorAuthorization.requireOperator(user);
    return titleDefRepository.findAllByOrderByZoneIdAscTierAsc().stream()
        .map(
            def ->
                AdminZoneTitleDefResDto.of(
                    def, userZoneTitleRepository.countByTitleDef_Id(def.getId())))
        .toList();
  }

  @Transactional
  public AdminZoneTitleDefResDto create(
      AuthenticatedUser user, AdminZoneTitleCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    String zoneId = ChatZone.fromString(request.zoneId()).name();
    if (titleDefRepository.existsByZoneIdAndTier(zoneId, request.tier())) {
      throw new ConflictException("error.zone_title.duplicate_tier");
    }
    if (titleDefRepository.existsByZoneIdAndRequiredSuccessCount(
        zoneId, request.requiredSuccessCount())) {
      throw new ConflictException("error.zone_title.duplicate_required_success_count");
    }
    requireMonotonic(zoneId, null, request.tier(), request.requiredSuccessCount());

    ZoneTitleDef def =
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode(zoneId + "_T" + request.tier())
                .zoneId(zoneId)
                .tier(request.tier())
                .requiredSuccessCount(request.requiredSuccessCount())
                .titleName(request.titleName())
                .style(request.style())
                .color(request.color())
                .build());

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("zoneId", zoneId);
    detail.put("tier", request.tier());
    detail.put("requiredSuccessCount", request.requiredSuccessCount());
    audit(user, "CREATE_TITLE_DEF", def.getId(), detail);
    return AdminZoneTitleDefResDto.of(def, 0L);
  }

  @Transactional
  public AdminZoneTitleDefResDto update(
      AuthenticatedUser user, UUID titleDefId, AdminZoneTitleUpdateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneTitleDef def =
        titleDefRepository
            .findById(titleDefId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_title.def_not_found"));
    if (!def.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_title.stale_revision");
    }

    Map<String, Object> before = snapshot(def);
    if (request.requiredSuccessCount() != null) {
      if (!request.requiredSuccessCount().equals(def.getRequiredSuccessCount())
          && titleDefRepository.existsByZoneIdAndRequiredSuccessCountAndIdNot(
              def.getZoneId(), request.requiredSuccessCount(), titleDefId)) {
        throw new ConflictException("error.zone_title.duplicate_required_success_count");
      }
      requireMonotonic(def.getZoneId(), titleDefId, def.getTier(), request.requiredSuccessCount());
    }
    def.applyEditable(request.titleName(), request.requiredSuccessCount());
    try {
      titleDefRepository.saveAndFlush(def);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.zone_title.stale_revision");
    }

    int backfilled = 0;
    if (Boolean.TRUE.equals(request.retroactive()) && request.requiredSuccessCount() != null) {
      backfilled = zoneTitleService.backfillGrants(def);
    }

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("before", before);
    detail.put("after", snapshot(def));
    detail.put("retroactive", request.retroactive());
    detail.put("backfilledCount", backfilled);
    audit(user, "PATCH_TITLE_DEF", def.getId(), detail);
    return AdminZoneTitleDefResDto.of(def, userZoneTitleRepository.countByTitleDef_Id(def.getId()));
  }

  @Transactional
  public void delete(AuthenticatedUser user, UUID titleDefId) {
    operatorAuthorization.requireOperator(user);
    ZoneTitleDef def =
        titleDefRepository
            .findById(titleDefId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_title.def_not_found"));
    if (userZoneTitleRepository.countByTitleDef_Id(titleDefId) > 0) {
      throw new ConflictException("error.zone_title.has_holders");
    }
    titleDefRepository.delete(def);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("zoneId", def.getZoneId());
    detail.put("tier", def.getTier());
    audit(user, "DELETE_TITLE_DEF", titleDefId, detail);
  }

  @Transactional(readOnly = true)
  public AdminZoneTitleHolderPageResDto holders(
      AuthenticatedUser user, UUID titleDefId, Integer page, Integer size) {
    operatorAuthorization.requireOperator(user);
    if (!titleDefRepository.existsById(titleDefId)) {
      throw new ResourceNotFoundException("error.zone_title.def_not_found");
    }
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    Page<UserZoneTitle> result =
        userZoneTitleRepository.findByTitleDef_Id(
            titleDefId,
            PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.desc("earnedAt"))));

    Map<UUID, User> usersById =
        userRepository
            .findAllById(
                result.getContent().stream().map(UserZoneTitle::getUserId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    List<AdminZoneTitleHolderItemResDto> items =
        result.getContent().stream()
            .map(t -> AdminZoneTitleHolderItemResDto.of(t, usersById.get(t.getUserId())))
            .toList();
    return new AdminZoneTitleHolderPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  private Map<String, Object> snapshot(ZoneTitleDef def) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("titleName", def.getTitleName());
    map.put("requiredSuccessCount", def.getRequiredSuccessCount());
    return map;
  }

  /**
   * 같은 구역 안에서 단계(tier)가 높을수록 달성 기준(requiredSuccessCount)도 커야 한다. excludeId는 수정 시 자기 자신을 비교 대상에서 빼기
   * 위한 것(생성 시에는 null).
   */
  void requireMonotonic(String zoneId, UUID excludeId, Integer tier, Integer requiredSuccessCount) {
    if (requiredSuccessCount <= 0) {
      throw new IllegalArgumentException("error.zone_title.invalid_required_success_count");
    }
    for (ZoneTitleDef sibling : titleDefRepository.findByZoneIdOrderByTierAsc(zoneId)) {
      if (excludeId != null && sibling.getId().equals(excludeId)) {
        continue;
      }
      if (sibling.getTier() < tier && sibling.getRequiredSuccessCount() >= requiredSuccessCount) {
        throw new IllegalArgumentException("error.zone_title.invalid_required_success_count");
      }
      if (sibling.getTier() > tier && sibling.getRequiredSuccessCount() <= requiredSuccessCount) {
        throw new IllegalArgumentException("error.zone_title.invalid_required_success_count");
      }
    }
  }

  void audit(AuthenticatedUser user, String action, UUID targetId, Map<String, Object> detail) {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(action)
            .targetType("ZONE_TITLE_DEF")
            .targetId(targetId)
            .detail(detail)
            .build());
  }
}
