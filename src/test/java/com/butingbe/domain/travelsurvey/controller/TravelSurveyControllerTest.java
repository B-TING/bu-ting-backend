package com.butingbe.domain.travelsurvey.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.travelsurvey.entity.TravelSurvey;
import com.butingbe.domain.travelsurvey.repository.TravelSurveyRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class TravelSurveyControllerTest extends AbstractContainerTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private UserRepository userRepository;

  @Autowired private TravelSurveyRepository travelSurveyRepository;

  @Autowired private OpaqueTokenService opaqueTokenService;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("Bearer 토큰 사용자 기준으로 여행 설문을 저장하고 조회한다")
  void upsertAndGetProfileWithBearerToken() throws Exception {
    User user = userRepository.save(createUser("survey-api@example.com", "survey-api"));
    String accessToken = opaqueTokenService.issue(user).accessToken();

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferredLanguage": "ko",
                      "isPlanned": true,
                      "isRelaxed": false,
                      "isSolo": true,
                      "isLight": true,
                      "isFamiliar": false,
                      "purposes": ["food", "scenery"],
                      "skippedSteps": [2],
                      "skippedAll": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferredLanguage").value("ko"))
        .andExpect(jsonPath("$.purposes[0]").value("food"))
        .andExpect(jsonPath("$.skippedSteps[0]").value(2));

    mockMvc
        .perform(
            get("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPlanned").value(true))
        .andExpect(jsonPath("$.purposes[1]").value("scenery"));
  }

  @Test
  @DisplayName("전체 스킵 요청은 성향 Boolean 값을 모두 null로 저장한다")
  void skippedAllClearsPreferenceBooleans() throws Exception {
    User user = userRepository.save(createUser("survey-skip@example.com", "survey-skip"));
    String accessToken = opaqueTokenService.issue(user).accessToken();

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "preferredLanguage": "en",
                      "isPlanned": true,
                      "isRelaxed": true,
                      "isSolo": true,
                      "isLight": true,
                      "isFamiliar": true,
                      "purposes": ["food"],
                      "skippedSteps": [0, 1, 2],
                      "skippedAll": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isPlanned").doesNotExist())
        .andExpect(jsonPath("$.skippedAll").value(true));

    TravelSurvey survey = travelSurveyRepository.findById(user.getId()).orElseThrow();
    assertThat(survey.getPlanned()).isNull();
    assertThat(survey.getRelaxed()).isNull();
    assertThat(survey.getSolo()).isNull();
    assertThat(survey.getLight()).isNull();
    assertThat(survey.getFamiliar()).isNull();
  }

  private User createUser(String email, String nickname) {
    return User.builder()
        .email(email)
        .provider("google")
        .providerId("google-" + nickname)
        .name(new Name("홍", "길동"))
        .nickname(nickname)
        .role(UserRole.USER)
        .build();
  }
}
