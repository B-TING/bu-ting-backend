package com.butingbe.domain.user.service;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.request.UpdateMyProfileReqDto;
import com.butingbe.domain.user.dto.response.MyProfileResDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
  private static final String DEVELOPMENT_PROVIDER = "development";
  private static final String DEVELOPMENT_ADMIN_PROVIDER_ID = "admin-token";
  private static final String DEVELOPMENT_ADMIN_FIRST_NAME = "관리자";
  private static final String DEVELOPMENT_ADMIN_LAST_NAME = "개발";
  private static final UUID LEGACY_DEVELOPMENT_ADMIN_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final UserRepository userRepository;
  private final OpaqueTokenRepository opaqueTokenRepository;

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

  @Override
  @Transactional
  public MyProfileResDto getMyProfile(AuthenticatedUser authenticatedUser) {
    return MyProfileResDto.from(findOrCreateAuthenticatedUser(authenticatedUser));
  }

  @Override
  @Transactional
  public MyProfileResDto updateMyProfile(
      AuthenticatedUser authenticatedUser, UpdateMyProfileReqDto request) {
    User user = findOrCreateAuthenticatedUser(authenticatedUser);
    user.updateProfile(
        request.nickname(), request.profileImageUrl(), request.firstName(), request.lastName());
    return MyProfileResDto.from(user);
  }

  @Override
  @Transactional
  public void deleteMyAccount(AuthenticatedUser authenticatedUser) {
    User user = findOrCreateAuthenticatedUser(authenticatedUser);
    opaqueTokenRepository.deleteByUserId(user.getId());
    userRepository.delete(user);
  }

  private User findOrCreateAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null) {
      throw new UnauthenticatedException();
    }

    if (authenticatedUser.isDevelopmentAdmin()) {
      return findOrCreateDevelopmentAdmin(authenticatedUser);
    }

    if (authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    return userRepository
        .findById(authenticatedUser.id())
        .orElseThrow(UnauthenticatedException::new);
  }

  private User findOrCreateDevelopmentAdmin(AuthenticatedUser authenticatedUser) {
    return userRepository
        .findByProviderAndProviderId(DEVELOPMENT_PROVIDER, DEVELOPMENT_ADMIN_PROVIDER_ID)
        .map(user -> replaceLegacyDevelopmentAdmin(user, authenticatedUser))
        .or(() -> userRepository.findByEmail(authenticatedUser.email()))
        .map(user -> replaceLegacyDevelopmentAdmin(user, authenticatedUser))
        .orElseGet(() -> userRepository.save(createDevelopmentAdmin(authenticatedUser)));
  }

  private User replaceLegacyDevelopmentAdmin(User user, AuthenticatedUser authenticatedUser) {
    if (!LEGACY_DEVELOPMENT_ADMIN_ID.equals(user.getId())) {
      return user;
    }

    opaqueTokenRepository.deleteByUserId(user.getId());
    userRepository.delete(user);
    userRepository.flush();
    return userRepository.save(createDevelopmentAdmin(authenticatedUser));
  }

  private User createDevelopmentAdmin(AuthenticatedUser authenticatedUser) {
    return User.builder()
        .email(authenticatedUser.email())
        .provider(DEVELOPMENT_PROVIDER)
        .providerId(DEVELOPMENT_ADMIN_PROVIDER_ID)
        .name(new Name(DEVELOPMENT_ADMIN_LAST_NAME, DEVELOPMENT_ADMIN_FIRST_NAME))
        .nickname(authenticatedUser.nickname())
        .role(UserRole.ADMIN)
        .build();
  }
}
