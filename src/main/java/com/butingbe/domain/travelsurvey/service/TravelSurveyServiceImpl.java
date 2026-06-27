package com.butingbe.domain.travelsurvey.service;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.travelsurvey.dto.response.TravelSurveyProfileResDto;
import com.butingbe.domain.travelsurvey.entity.TravelSurvey;
import com.butingbe.domain.travelsurvey.repository.TravelSurveyRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelSurveyServiceImpl implements TravelSurveyService {
  private static final String DEVELOPMENT_PROVIDER = "development";
  private static final String DEVELOPMENT_ADMIN_PROVIDER_ID = "admin-token";
  private static final String DEVELOPMENT_ADMIN_FIRST_NAME = "관리자";
  private static final String DEVELOPMENT_ADMIN_LAST_NAME = "개발";
  private static final UUID LEGACY_DEVELOPMENT_ADMIN_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final TravelSurveyRepository travelSurveyRepository;
  private final UserRepository userRepository;
  private final OpaqueTokenRepository opaqueTokenRepository;

  @Override
  @Transactional
  public TravelSurveyProfileResDto upsertProfile(
      AuthenticatedUser authenticatedUser, TravelSurveyProfileReqDto request) {
    User user = findOrCreateAuthenticatedUser(authenticatedUser);
    TravelSurvey survey =
        travelSurveyRepository
            .findById(user.getId())
            .map(
                existing -> {
                  existing.update(request);
                  return existing;
                })
            .orElseGet(() -> travelSurveyRepository.save(new TravelSurvey(user, request)));

    return TravelSurveyProfileResDto.from(survey);
  }

  @Override
  @Transactional
  public TravelSurveyProfileResDto getProfile(AuthenticatedUser authenticatedUser) {
    User user = findOrCreateAuthenticatedUser(authenticatedUser);
    return travelSurveyRepository
        .findById(user.getId())
        .map(TravelSurveyProfileResDto::from)
        .orElseThrow(() -> new IllegalArgumentException("travel survey profile not found."));
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
