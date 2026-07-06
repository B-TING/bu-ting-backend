package com.butingbe.domain.travelteam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.service.TravelTeamService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class TravelTeamControllerTest {

  private MockMvc mockMvc;
  private FakeTravelTeamService travelTeamService;

  @BeforeEach
  void setUp() {
    travelTeamService = new FakeTravelTeamService();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TravelTeamController(travelTeamService))
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .build();
  }

  @Test
  @DisplayName("verify invite token returns response envelope")
  void verifyInvite() throws Exception {
    mockMvc
        .perform(get("/travel/team/invites/verify").param("token", "invite-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.travelId").value(FakeTravelTeamService.TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.data.valid").value(true));
  }

  @Test
  @DisplayName("create invite link returns invite link")
  void createInviteLink() throws Exception {
    mockMvc
        .perform(post("/travel/team/{travelId}/invite", FakeTravelTeamService.TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inviteLink").value("https://yourdomain.com/invite?token=invite-token"));

    assertThat(travelTeamService.createdInviteTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
  }

  @Test
  @DisplayName("accept invite returns response envelope")
  void acceptInvite() throws Exception {
    mockMvc
        .perform(post("/travel/team/invites/accept").param("token", "invite-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.travelName").value("Busan"));
  }

  @Test
  @DisplayName("exit travel delegates to service")
  void exitTravel() throws Exception {
    mockMvc
        .perform(delete("/travel/team/{travelId}/members/me", FakeTravelTeamService.TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(travelTeamService.exitedTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
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
        return new AuthenticatedUser(UUID.randomUUID(), "user@example.com", "tester", List.of());
      }
    };
  }

  static class FakeTravelTeamService extends TravelTeamService {
    static final UUID TRAVEL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    UUID createdInviteTravelId;
    UUID exitedTravelId;

    FakeTravelTeamService() {
      super(null, null, null, null);
    }

    @Override
    public InviteVerificationResponse verifyToken(String token) {
      return new InviteVerificationResponse(TRAVEL_ID, "Busan", true);
    }

    @Override
    public String createInviteLink(AuthenticatedUser authenticatedUser, UUID travelId) {
      createdInviteTravelId = travelId;
      return "https://yourdomain.com/invite?token=invite-token";
    }

    @Override
    public InviteVerificationResponse acceptInvite(AuthenticatedUser authenticatedUser, String token) {
      return new InviteVerificationResponse(TRAVEL_ID, "Busan", true);
    }

    @Override
    public void exitTravel(AuthenticatedUser authenticatedUser, UUID travelId) {
      exitedTravelId = travelId;
    }
  }
}
