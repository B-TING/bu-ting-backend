package com.butingbe.domain.reward.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.dto.request.AdminRewardCatalogCreateReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardCatalogUpdateReqDto;
import com.butingbe.domain.reward.dto.response.AdminRewardGrantPageResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardGrantResDto;
import com.butingbe.domain.reward.dto.response.RewardCatalogResDto;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 보상 카탈로그 관리와 지급 이력 조회. ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminRewardCatalogService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final RewardCatalogRepository rewardCatalogRepository;
  private final RewardGrantRepository rewardGrantRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional
  public RewardCatalogResDto create(
      AuthenticatedUser user, AdminRewardCatalogCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    if (rewardCatalogRepository.existsByCode(request.code())) {
      throw new DuplicateResourceException("error.reward.catalog_duplicate");
    }
    RewardCatalog catalog =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(parseType(request.rewardType()))
                .code(request.code())
                .name(request.name())
                .pointAmount(request.pointAmount())
                .imageFileKey(request.imageFileKey())
                .stock(request.stock())
                .monthlyCap(request.monthlyCap())
                .validDays(request.validDays())
                .active(request.active())
                .build());
    return RewardCatalogResDto.from(catalog);
  }

  @Transactional(readOnly = true)
  public List<RewardCatalogResDto> list(AuthenticatedUser user, String rewardType, Boolean active) {
    operatorAuthorization.requireOperator(user);
    RewardType typeFilter =
        rewardType == null || rewardType.isBlank() ? null : parseType(rewardType);
    Specification<RewardCatalog> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (typeFilter != null) {
            predicates.add(cb.equal(root.get("rewardType"), typeFilter));
          }
          if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    return rewardCatalogRepository.findAll(spec, Sort.by(Sort.Order.asc("code"))).stream()
        .map(RewardCatalogResDto::from)
        .toList();
  }

  @Transactional
  public RewardCatalogResDto update(
      AuthenticatedUser user, UUID rewardId, AdminRewardCatalogUpdateReqDto request) {
    operatorAuthorization.requireOperator(user);
    RewardCatalog catalog =
        rewardCatalogRepository
            .findById(rewardId)
            .orElseThrow(() -> new ResourceNotFoundException("error.reward.catalog_not_found"));
    catalog.update(request.name(), request.stock(), request.monthlyCap(), request.active());
    return RewardCatalogResDto.from(catalog);
  }

  @Transactional(readOnly = true)
  public AdminRewardGrantPageResDto grants(
      AuthenticatedUser user, UUID rewardId, String cursor, Integer size) {
    operatorAuthorization.requireOperator(user);
    if (!rewardCatalogRepository.existsById(rewardId)) {
      throw new ResourceNotFoundException("error.reward.catalog_not_found");
    }
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);

    Specification<RewardGrant> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("reward").get("id"), rewardId));
          if (decoded != null) {
            Predicate earlier = cb.lessThan(root.get("grantedAt"), decoded.grantedAt());
            Predicate sameTimeLowerId =
                cb.and(
                    cb.equal(root.get("grantedAt"), decoded.grantedAt()),
                    cb.lessThan(root.get("id"), decoded.id()));
            predicates.add(cb.or(earlier, sameTimeLowerId));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    List<RewardGrant> rows =
        rewardGrantRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.desc("grantedAt"), Sort.Order.desc("id"))))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<RewardGrant> page = hasNext ? rows.subList(0, pageSize) : rows;
    List<AdminRewardGrantResDto> items = page.stream().map(AdminRewardGrantResDto::from).toList();
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new AdminRewardGrantPageResDto(items, nextCursor, hasNext);
  }

  private RewardType parseType(String rewardType) {
    try {
      return RewardType.valueOf(rewardType.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.reward.invalid_type");
    }
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String encodeCursor(RewardGrant grant) {
    String raw = grant.getGrantedAt() + "|" + grant.getId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid reward grant cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid reward grant cursor.");
    }
  }

  private record Cursor(OffsetDateTime grantedAt, UUID id) {}
}
