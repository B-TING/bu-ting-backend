package com.butingbe.domain.reward.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.response.PointLedgerItemResDto;
import com.butingbe.domain.reward.dto.response.PointLedgerPageResDto;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto.BadgeGroup;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto.BadgeItem;
import com.butingbe.domain.reward.service.RewardQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class UserRewardControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private RewardQueryService rewardQueryService;
  @InjectMocks private UserRewardController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  @DisplayName("보상 요약을 200으로 반환한다")
  void myRewards() throws Exception {
    when(rewardQueryService.myRewards(any()))
        .thenReturn(
            new UserRewardsResDto(
                350,
                List.of(
                    new BadgeGroup(
                        "SUYEONG_NAMGU",
                        List.of(
                            new BadgeItem(
                                "SPOT_GWANGAN_BRIDGE",
                                "광안대교 스팟",
                                "https://signed/badge.png",
                                OffsetDateTime.now())))),
                List.of()));

    mockMvc
        .perform(get("/users/me/rewards"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pointBalance").value(350))
        .andExpect(jsonPath("$.data.badges[0].zoneId").value("SUYEONG_NAMGU"))
        .andExpect(jsonPath("$.data.badges[0].items[0].code").value("SPOT_GWANGAN_BRIDGE"));
  }

  @Test
  @DisplayName("포인트 원장을 커서 페이징으로 반환한다")
  void pointLedger() throws Exception {
    when(rewardQueryService.pointLedger(any(), isNull(), eq(2)))
        .thenReturn(
            new PointLedgerPageResDto(
                List.of(
                    new PointLedgerItemResDto(
                        UUID.randomUUID().toString(), 50, "BASE", null, OffsetDateTime.now())),
                "next",
                true));

    mockMvc
        .perform(get("/users/me/point-ledger").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].amount").value(50))
        .andExpect(jsonPath("$.data.nextCursor").value("next"))
        .andExpect(jsonPath("$.data.hasNext").value(true));
  }

  private HandlerMethodArgumentResolver authenticatedUserResolver() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest,
          WebDataBinderFactory binderFactory) {
        return new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
      }
    };
  }
}
