package com.butingbe.domain.travelexpense.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse;
import com.butingbe.domain.travelexpense.repository.TravelSettlementRepository;
import com.butingbe.domain.travelexpense.repository.TravelSettlementTransferRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 저장소가 정산 대상 사용자를 돌려주지 못하는 상황을 목으로 구성해 검증한다. */
@ExtendWith(MockitoExtension.class)
class TravelSettlementServiceMockTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID LEADER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID DEBTOR_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

  @Mock private TravelRepository travelRepository;
  @Mock private TravelExpenseService travelExpenseService;
  @Mock private TravelSettlementRepository travelSettlementRepository;
  @Mock private TravelSettlementTransferRepository transferRepository;
  @Mock private TravelMemberAuthorization travelMemberAuthorization;
  @Mock private UserRepository userRepository;

  @InjectMocks private TravelSettlementService travelSettlementService;

  @Test
  @DisplayName("정산 대상 사용자를 조회하지 못하면 확정을 중단한다")
  void rejectsConfirmationWhenTransferUserIsMissing() {
    AuthenticatedUser leader =
        new AuthenticatedUser(LEADER_ID, "leader@example.com", "leader", List.of());
    User leaderUser = user(LEADER_ID, "leader");

    when(travelRepository.findByIdForUpdate(TRAVEL_ID)).thenReturn(Optional.of(travel()));
    when(travelMemberAuthorization.requireLeader(any(), any(), any()))
        .thenReturn(
            TravelMember.builder()
                .travel(travel())
                .user(leaderUser)
                .role(TravelTeamRole.LEADER)
                .build());
    when(travelSettlementRepository.findByTravel_Id(TRAVEL_ID)).thenReturn(Optional.empty());
    when(travelExpenseService.getExpenseSummary(any(), any(), any(), any()))
        .thenReturn(
            new TravelExpenseSummaryResponse(
                TRAVEL_ID,
                1L,
                List.of(
                    new TravelExpenseSummaryResponse.CurrencySummary(
                        "KRW",
                        10000L,
                        List.of(),
                        List.of(
                            new TravelExpenseSummaryResponse.MemberSummary(
                                DEBTOR_ID, "채무자", 0L, 5000L, -5000L),
                            new TravelExpenseSummaryResponse.MemberSummary(
                                LEADER_ID, "채권자", 10000L, 5000L, 5000L)))),
                null,
                null));
    when(travelSettlementRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findAllById(any())).thenReturn(List.of());

    assertThatThrownBy(() -> travelSettlementService.confirmSettlement(leader, TRAVEL_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Settlement user not found.");
  }

  private Travel travel() {
    Travel travel =
        Travel.builder()
            .title("부산")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2026, 9, 3))
            .build();
    ReflectionTestUtils.setField(travel, "id", TRAVEL_ID);
    return travel;
  }

  private User user(UUID id, String nickname) {
    User created =
        User.builder()
            .email(nickname + "@example.com")
            .provider("google")
            .providerId("google-" + nickname)
            .name(new Name("Kim", "Tester"))
            .nickname(nickname)
            .role(UserRole.USER)
            .build();
    ReflectionTestUtils.setField(created, "id", id);
    return created;
  }
}
