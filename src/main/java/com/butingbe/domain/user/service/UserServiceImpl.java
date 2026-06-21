package com.butingbe.domain.user.service;

import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
  private final UserRepository userRepository;

  private static boolean isInvalidEmailParam(String email) {
    return email == null || email.isBlank() || "null".equals(email);
  }

  @Override
  @Transactional
  public void signUp(SignUpReqDto request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new DuplicateResourceException();
    }

    User user =
        User.builder()
            .email(request.email())
            .name(request.toEmbeddedName())
            .nickname(request.nickname())
            .role(UserRole.USER)
            .build();

    userRepository.save(user);
  }

  @Override
  public UserResDto signIn(String email) {
    // 이메일 파라미터가 유효하지 않으면 즉시 401 내장 메시지 유도
    if (isInvalidEmailParam(email)) {
      throw new UnauthenticatedException();
    }

    // DB에 유저가 없어도 즉시 401 내장 메시지 유도
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(UnauthenticatedException::new); // 💡 깔끔한 메서드 참조 표기법

    return UserResDto.from(user);
  }
}
