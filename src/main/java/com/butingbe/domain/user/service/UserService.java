package com.butingbe.domain.user.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.UpdateMyProfileReqDto;
import com.butingbe.domain.user.dto.response.MyProfileResDto;

public interface UserService {
  MyProfileResDto getMyProfile(AuthenticatedUser authenticatedUser);

  MyProfileResDto updateMyProfile(
      AuthenticatedUser authenticatedUser, UpdateMyProfileReqDto request);

  void deleteMyAccount(AuthenticatedUser authenticatedUser);
}
