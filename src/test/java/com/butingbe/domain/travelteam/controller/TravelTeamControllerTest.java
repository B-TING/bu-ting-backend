package com.butingbe.domain.travelteam.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.dto.TravelInviteLinkInfoResponse;
import com.butingbe.domain.travelteam.dto.TravelMemberResponse;
import com.butingbe.domain.travelteam.dto.TravelTeamTravelResponse;
import com.butingbe.domain.travelteam.dto.request.TravelLeaderTransferRequest;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.service.TravelTeamService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
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
  @DisplayName("get my travels returns filtered response envelope")
  void getMyTravels() throws Exception {
    mockMvc
        .perform(get("/travel/team/my-travels").param("status", "IN_PROGRESS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].travelId").value(FakeTravelTeamService.TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"));

    assertThat(travelTeamService.myTravelsStatus).isEqualTo(TravelStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("get travel members returns response envelope")
  void getTravelMembers() throws Exception {
    mockMvc
        .perform(get("/travel/team/{travelId}/members", FakeTravelTeamService.TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].userId").value(FakeTravelTeamService.USER_ID.toString()))
        .andExpect(jsonPath("$.data[0].nickname").value("tester"))
        .andExpect(jsonPath("$.data[0].role").value("LEADER"));

    assertThat(travelTeamService.membersTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
  }

  @Test
  @DisplayName("transfer leader delegates to service")
  void transferLeader() throws Exception {
    mockMvc
        .perform(
            patch("/travel/team/{travelId}/leader", FakeTravelTeamService.TRAVEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"newLeaderUserId":"50000000-0000-0000-0000-000000000001"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(travelTeamService.transferredTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
    assertThat(travelTeamService.transferredNewLeaderUserId)
        .isEqualTo(FakeTravelTeamService.USER_ID);
  }

  @Test
  @DisplayName("remove member delegates to service")
  void removeMember() throws Exception {
    mockMvc
        .perform(
            delete(
                "/travel/team/{travelId}/members/{userId}",
                FakeTravelTeamService.TRAVEL_ID,
                FakeTravelTeamService.USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(travelTeamService.removedTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
    assertThat(travelTeamService.removedUserId).isEqualTo(FakeTravelTeamService.USER_ID);
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
        .andExpect(
            jsonPath("$.inviteLink").value("https://yourdomain.com/invite?token=invite-token"));

    assertThat(travelTeamService.createdInviteTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
  }

  @Test
  @DisplayName("get invite link returns response envelope")
  void getInviteLink() throws Exception {
    mockMvc
        .perform(get("/travel/team/{travelId}/invite", FakeTravelTeamService.TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.inviteLink")
                .value("https://yourdomain.com/invite?token=invite-token"));

    assertThat(travelTeamService.inviteLinkTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
  }

  @Test
  @DisplayName("delete invite link delegates to service")
  void deleteInviteLink() throws Exception {
    mockMvc
        .perform(delete("/travel/team/{travelId}/invite", FakeTravelTeamService.TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(travelTeamService.deletedInviteTravelId).isEqualTo(FakeTravelTeamService.TRAVEL_ID);
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
    static final UUID MEMBER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    static final UUID USER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    UUID createdInviteTravelId;
    UUID exitedTravelId;
    UUID membersTravelId;
    UUID transferredTravelId;
    UUID transferredNewLeaderUserId;
    UUID removedTravelId;
    UUID removedUserId;
    UUID inviteLinkTravelId;
    UUID deletedInviteTravelId;
    TravelStatus myTravelsStatus;

    FakeTravelTeamService() {
      super(null, null, null, null, "https://yourdomain.com/invite");
    }

    @Override
    public InviteVerificationResponse verifyToken(String token) {
      return new InviteVerificationResponse(TRAVEL_ID, "Busan", true);
    }

    @Override
    public List<TravelTeamTravelResponse> getMyTravels(
        AuthenticatedUser authenticatedUser, TravelStatus status) {
      myTravelsStatus = status;
      return List.of(
          new TravelTeamTravelResponse(
              TRAVEL_ID,
              "Busan",
              LocalDate.of(2026, 8, 1),
              LocalDate.of(2026, 8, 3),
              TravelStatus.IN_PROGRESS,
              TravelTeamRole.MEMBER,
              null));
    }

    @Override
    public List<TravelMemberResponse> getTravelMembers(
        AuthenticatedUser authenticatedUser, UUID travelId) {
      membersTravelId = travelId;
      return List.of(
          new TravelMemberResponse(
              MEMBER_ID, USER_ID, "tester@example.com", "tester", null, TravelTeamRole.LEADER));
    }

    @Override
    public void transferLeader(
        AuthenticatedUser authenticatedUser, UUID travelId, TravelLeaderTransferRequest request) {
      transferredTravelId = travelId;
      transferredNewLeaderUserId = request.newLeaderUserId();
    }

    @Override
    public void removeMember(AuthenticatedUser authenticatedUser, UUID travelId, UUID targetUserId) {
      removedTravelId = travelId;
      removedUserId = targetUserId;
    }

    @Override
    public String createInviteLink(AuthenticatedUser authenticatedUser, UUID travelId) {
      createdInviteTravelId = travelId;
      return "https://yourdomain.com/invite?token=invite-token";
    }

    @Override
    public TravelInviteLinkInfoResponse getInviteLink(
        AuthenticatedUser authenticatedUser, UUID travelId) {
      inviteLinkTravelId = travelId;
      return new TravelInviteLinkInfoResponse(
          "https://yourdomain.com/invite?token=invite-token", OffsetDateTime.now().plusHours(1));
    }

    @Override
    public void deleteInviteLink(AuthenticatedUser authenticatedUser, UUID travelId) {
      deletedInviteTravelId = travelId;
    }

    @Override
    public InviteVerificationResponse acceptInvite(
        AuthenticatedUser authenticatedUser, String token) {
      return new InviteVerificationResponse(TRAVEL_ID, "Busan", true);
    }

    @Override
    public void exitTravel(AuthenticatedUser authenticatedUser, UUID travelId) {
      exitedTravelId = travelId;
    }
  }
}
