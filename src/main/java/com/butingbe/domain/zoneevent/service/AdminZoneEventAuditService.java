package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventAuditItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventAuditPageResDto;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 행위 감사 이력 조회. ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventAuditService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventAuditLogRepository auditLogRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public AdminZoneEventAuditPageResDto list(
      AuthenticatedUser user,
      String resourceType,
      UUID resourceId,
      UUID actorId,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String targetType = resourceType == null || resourceType.isBlank() ? null : resourceType;

    Page<com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog> result =
        auditLogRepository.searchForAdmin(
            targetType, resourceId, actorId, from, to, PageRequest.of(pageNumber - 1, pageSize));

    var items = result.getContent().stream().map(AdminZoneEventAuditItemResDto::from).toList();
    return new AdminZoneEventAuditPageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }
}
