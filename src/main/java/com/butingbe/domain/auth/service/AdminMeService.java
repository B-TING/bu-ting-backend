package com.butingbe.domain.auth.service;

import com.butingbe.domain.auth.dto.response.AdminMeResDto;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 관리자 페이지 자기 정보 조회. ROLE_ADMIN/MANAGER만 통과한다. */
@Service
@RequiredArgsConstructor
public class AdminMeService {

  private final OperatorAuthorization operatorAuthorization;

  public AdminMeResDto getMe(AuthenticatedUser user) {
    operatorAuthorization.requireOperator(user);
    return AdminMeResDto.from(user);
  }
}
