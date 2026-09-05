package com.butingbe.domain.travelexpense.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TravelSettlementTransferTest {

  private static final UUID SENDER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID RECEIVER_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

  @Test
  @DisplayName("보내는 사람과 받는 사람이 다르고 금액이 양수면 송금 항목을 만든다")
  void createsTransfer() {
    TravelSettlementTransfer transfer =
        TravelSettlementTransfer.builder()
            .settlement(new TravelSettlement())
            .currency("KRW")
            .fromUser(user(SENDER_ID, "sender"))
            .toUser(user(RECEIVER_ID, "receiver"))
            .amount(15000L)
            .build();

    assertThat(transfer.getCurrency()).isEqualTo("KRW");
    assertThat(transfer.getAmount()).isEqualTo(15000L);
    assertThat(transfer.getFromUser().getId()).isEqualTo(SENDER_ID);
    assertThat(transfer.getToUser().getId()).isEqualTo(RECEIVER_ID);
  }

  @Test
  @DisplayName("보내는 사람과 받는 사람이 같으면 송금 항목을 만들 수 없다")
  void rejectsSameSenderAndReceiver() {
    User same = user(SENDER_ID, "same");

    assertThatThrownBy(
            () ->
                TravelSettlementTransfer.builder()
                    .settlement(new TravelSettlement())
                    .currency("KRW")
                    .fromUser(same)
                    .toUser(same)
                    .amount(15000L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Settlement sender and receiver must be different.");
  }

  @Test
  @DisplayName("금액이 없거나 0 이하면 송금 항목을 만들 수 없다")
  void rejectsNonPositiveAmount() {
    User sender = user(SENDER_ID, "sender");
    User receiver = user(RECEIVER_ID, "receiver");

    assertThatThrownBy(
            () ->
                TravelSettlementTransfer.builder()
                    .settlement(new TravelSettlement())
                    .currency("KRW")
                    .fromUser(sender)
                    .toUser(receiver)
                    .amount(null)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Settlement amount must be positive.");
    assertThatThrownBy(
            () ->
                TravelSettlementTransfer.builder()
                    .settlement(new TravelSettlement())
                    .currency("KRW")
                    .fromUser(sender)
                    .toUser(receiver)
                    .amount(0L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Settlement amount must be positive.");
  }

  @Test
  @DisplayName("필수 값이 없으면 송금 항목을 만들 수 없다")
  void rejectsMissingRequiredValues() {
    assertThatThrownBy(
            () -> TravelSettlementTransfer.builder().settlement(null).currency("KRW").build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Settlement is required.");
    assertThatThrownBy(
            () ->
                TravelSettlementTransfer.builder()
                    .settlement(new TravelSettlement())
                    .currency(null)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Currency is required.");
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
