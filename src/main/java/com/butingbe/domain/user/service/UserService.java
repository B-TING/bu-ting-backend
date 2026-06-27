package com.butingbe.domain.user.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.request.UpdateMyProfileReqDto;
import com.butingbe.domain.user.dto.response.MyProfileResDto;
import com.butingbe.domain.user.dto.response.UserResDto;

public interface UserService {
  void signUp(SignUpReqDto request);

  UserResDto signIn(String email);

  MyProfileResDto getMyProfile(AuthenticatedUser authenticatedUser);

  MyProfileResDto updateMyProfile(
      AuthenticatedUser authenticatedUser, UpdateMyProfileReqDto request);

  void deleteMyAccount(AuthenticatedUser authenticatedUser);
}
