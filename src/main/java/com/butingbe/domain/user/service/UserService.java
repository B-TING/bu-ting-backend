package com.butingbe.domain.user.service;

import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;

public interface UserService {
  void signUp(SignUpReqDto request);

  UserResDto signIn(String email);
}
