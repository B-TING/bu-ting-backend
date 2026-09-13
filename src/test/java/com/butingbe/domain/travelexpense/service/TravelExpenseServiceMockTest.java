package com.butingbe.domain.travelexpense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository;
import com.butingbe.domain.travelexpense.repository.TravelSettlementRepository;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 통화 합계가 0인 집계 결과를 목으로 만들어 비율 계산의 0 나눗셈 방지 분기를 검증한다. */
@ExtendWith(MockitoExtension.class)
class TravelExpenseServiceMockTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private TravelRepository travelRepository;
  @Mock private TravelMemberRepository travelMemberRepository;
  @Mock private TravelExpenseRepository travelExpenseRepository;
  @Mock private TravelExpenseShareRepository travelExpenseShareRepository;
  @Mock private TravelMemberAuthorization travelMemberAuthorization;
  @Mock private TravelSettlementRepository travelSettlementRepository;

  @InjectMocks private TravelExpenseService travelExpenseService;

  @Test
  @DisplayName("통화 합계가 0이면 카테고리 비율을 0.00으로 계산한다")
  void ratioIsZeroWhenCurrencyTotalIsZero() {
    AuthenticatedUser user = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());

    TravelExpenseRepository.CurrencyTotal total = mock(TravelExpenseRepository.CurrencyTotal.class);
    when(total.getCurrency()).thenReturn("KRW");
    when(total.getTotalAmount()).thenReturn(0L);
    when(total.getExpenseCount()).thenReturn(1L);

    TravelExpenseRepository.CategoryTotal category =
        mock(TravelExpenseRepository.CategoryTotal.class);
    when(category.getCurrency()).thenReturn("KRW");
    when(category.getCategory()).thenReturn(ExpenseCategory.FOOD);
    when(category.getAmount()).thenReturn(0L);
    when(category.getExpenseCount()).thenReturn(1L);

    when(travelRepository.existsById(TRAVEL_ID)).thenReturn(true);
    when(travelExpenseRepository.summarizeCurrencies(TRAVEL_ID, null, null))
        .thenReturn(List.of(total));
    when(travelExpenseRepository.summarizeCategories(TRAVEL_ID, null, null))
        .thenReturn(List.of(category));
    when(travelExpenseRepository.summarizePaidAmounts(TRAVEL_ID, null, null)).thenReturn(List.of());
    when(travelExpenseShareRepository.summarizeShareAmounts(TRAVEL_ID, null, null))
        .thenReturn(List.of());

    var summary = travelExpenseService.getExpenseSummary(user, TRAVEL_ID, null, null);

    assertThat(summary.currencySummaries()).hasSize(1);
    assertThat(summary.currencySummaries().get(0).categorySummaries().get(0).ratio())
        .isEqualByComparingTo(new BigDecimal("0.00"));
  }
}
