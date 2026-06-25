package com.butingbe.domain.travelsurvey.service;

import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.travelsurvey.dto.response.TravelSurveyProfileResDto;
import com.butingbe.domain.travelsurvey.entity.TravelSurvey;
import com.butingbe.domain.travelsurvey.repository.TravelSurveyRepository;
import com.butingbe.domain.user.entity.User;
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

  private final TravelSurveyRepository travelSurveyRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional
  public TravelSurveyProfileResDto upsertProfile(UUID userId, TravelSurveyProfileReqDto request) {
    User user = userRepository.findById(userId).orElseThrow(UnauthenticatedException::new);
    TravelSurvey survey =
        travelSurveyRepository
            .findById(userId)
            .map(
                existing -> {
                  existing.update(request);
                  return existing;
                })
            .orElseGet(() -> travelSurveyRepository.save(new TravelSurvey(user, request)));

    return TravelSurveyProfileResDto.from(survey);
  }
}
