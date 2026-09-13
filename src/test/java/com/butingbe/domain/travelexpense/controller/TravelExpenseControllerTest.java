package com.butingbe.domain.travelexpense.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseUpdateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseDetailResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseListResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.ExpenseSplitType;
import com.butingbe.domain.travelexpense.service.TravelExpenseService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class TravelExpenseControllerTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID EXPENSE_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID PAYER_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

  private MockMvc mockMvc;

  @Mock private TravelExpenseService travelExpenseService;

  @InjectMocks private TravelExpenseController travelExpenseController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelExpenseController)
            .setCustomArgumentResolvers(
                authenticatedUserResolver(), new PageableHandlerMethodArgumentResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("경비 요약을 조회하면 통화별 합계를 반환한다")
  void getExpenseSummaryReturnsCurrencyTotals() throws Exception {
    when(travelExpenseService.getExpenseSummary(any(), eq(TRAVEL_ID), any(), any()))
        .thenReturn(
            new TravelExpenseSummaryResponse(
                TRAVEL_ID,
                2L,
                List.of(
                    new TravelExpenseSummaryResponse.CurrencySummary(
                        "KRW",
                        30000L,
                        List.of(
                            new TravelExpenseSummaryResponse.CategorySummary(
                                ExpenseCategory.FOOD, 30000L, 2L, new BigDecimal("1.00"))),
                        List.of(
                            new TravelExpenseSummaryResponse.MemberSummary(
                                USER_ID, "tester", 30000L, 15000L, 15000L)))),
                null,
                null));

    mockMvc
        .perform(get("/travels/{travelId}/expenses/summary", TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.travelId").value(TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.expenseCount").value(2))
        .andExpect(jsonPath("$.currencySummaries[0].currency").value("KRW"))
        .andExpect(jsonPath("$.currencySummaries[0].totalAmount").value(30000))
        .andExpect(jsonPath("$.currencySummaries[0].categorySummaries[0].category").value("FOOD"))
        .andExpect(jsonPath("$.currencySummaries[0].memberSummaries[0].balance").value(15000));
  }

  @Test
  @DisplayName("기간 파라미터를 넘기면 서비스에 그대로 전달된다")
  void getExpenseSummaryPassesDateRange() throws Exception {
    LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 9, 30, 23, 59);
    when(travelExpenseService.getExpenseSummary(any(), eq(TRAVEL_ID), eq(from), eq(to)))
        .thenReturn(new TravelExpenseSummaryResponse(TRAVEL_ID, 0L, List.of(), from, to));

    mockMvc
        .perform(
            get("/travels/{travelId}/expenses/summary", TRAVEL_ID)
                .param("from", "2026-09-01T00:00:00")
                .param("to", "2026-09-30T23:59:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expenseCount").value(0));

    verify(travelExpenseService).getExpenseSummary(any(), eq(TRAVEL_ID), eq(from), eq(to));
  }

  @Test
  @DisplayName("경비 목록을 조회하면 페이지 정보와 함께 반환한다")
  void getExpensesReturnsPagedList() throws Exception {
    when(travelExpenseService.getExpenses(
            any(), eq(TRAVEL_ID), any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(
            new TravelExpenseListResponse(
                List.of(
                    new TravelExpenseListResponse.ExpenseSummary(
                        EXPENSE_ID,
                        "저녁식사",
                        30000L,
                        "KRW",
                        ExpenseCategory.FOOD,
                        new TravelExpenseListResponse.PayerResponse(PAYER_ID, "결제자"),
                        2L,
                        LocalDateTime.of(2026, 9, 5, 19, 0),
                        LocalDateTime.of(2026, 9, 5, 19, 5))),
                0,
                20,
                1L,
                1));

    mockMvc
        .perform(get("/travels/{travelId}/expenses", TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].expenseId").value(EXPENSE_ID.toString()))
        .andExpect(jsonPath("$.content[0].title").value("저녁식사"))
        .andExpect(jsonPath("$.content[0].payer.nickname").value("결제자"))
        .andExpect(jsonPath("$.content[0].participantCount").value(2))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  @DisplayName("payerId가 없으면 payerUserId를 대신 서비스에 전달한다")
  void getExpensesFallsBackToPayerUserId() throws Exception {
    when(travelExpenseService.getExpenses(
            any(), eq(TRAVEL_ID), any(), any(), any(), eq(PAYER_ID), any(Pageable.class)))
        .thenReturn(new TravelExpenseListResponse(List.of(), 0, 20, 0L, 0));

    mockMvc
        .perform(
            get("/travels/{travelId}/expenses", TRAVEL_ID)
                .param("payerUserId", PAYER_ID.toString())
                .param("category", "FOOD"))
        .andExpect(status().isOk());

    verify(travelExpenseService)
        .getExpenses(
            any(),
            eq(TRAVEL_ID),
            eq(ExpenseCategory.FOOD),
            any(),
            any(),
            eq(PAYER_ID),
            any(Pageable.class));
  }

  @Test
  @DisplayName("단건 경비를 조회하면 상세 정보를 반환한다")
  void getExpenseReturnsDetail() throws Exception {
    when(travelExpenseService.getExpense(any(), eq(TRAVEL_ID), eq(EXPENSE_ID)))
        .thenReturn(detailResponse());

    mockMvc
        .perform(get("/travels/{travelId}/expenses/{expenseId}", TRAVEL_ID, EXPENSE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expenseId").value(EXPENSE_ID.toString()))
        .andExpect(jsonPath("$.payer.nickname").value("결제자"))
        .andExpect(jsonPath("$.shares[0].shareAmount").value(15000))
        .andExpect(jsonPath("$.editable").value(true));
  }

  @Test
  @DisplayName("경비를 수정하면 갱신된 상세 정보를 반환한다")
  void updateExpenseReturnsUpdatedDetail() throws Exception {
    when(travelExpenseService.updateExpense(
            any(), eq(TRAVEL_ID), eq(EXPENSE_ID), any(TravelExpenseUpdateRequest.class)))
        .thenReturn(detailResponse());

    mockMvc
        .perform(
            put("/travels/{travelId}/expenses/{expenseId}", TRAVEL_ID, EXPENSE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "저녁식사",
                      "amount": 30000,
                      "currency": "KRW",
                      "category": "FOOD",
                      "payerId": "%s",
                      "participantIds": ["%s", "%s"],
                      "spentAt": "2026-09-05T19:00:00",
                      "memo": "회식"
                    }
                    """
                        .formatted(PAYER_ID, USER_ID, PAYER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expenseId").value(EXPENSE_ID.toString()))
        .andExpect(jsonPath("$.title").value("저녁식사"));
  }

  @Test
  @DisplayName("경비를 등록하면 201과 생성된 경비 정보를 반환한다")
  void createEqualExpenseReturnsCreated() throws Exception {
    when(travelExpenseService.createEqualExpense(
            any(), eq(TRAVEL_ID), any(TravelExpenseCreateRequest.class)))
        .thenReturn(
            new TravelExpenseCreateResponse(
                EXPENSE_ID,
                TRAVEL_ID,
                "저녁식사",
                30000L,
                "KRW",
                ExpenseCategory.FOOD,
                PAYER_ID,
                USER_ID,
                ExpenseSplitType.EQUAL,
                LocalDateTime.of(2026, 9, 5, 19, 0),
                "회식",
                List.of(
                    new TravelExpenseCreateResponse.ShareResponse(USER_ID, 15000L),
                    new TravelExpenseCreateResponse.ShareResponse(PAYER_ID, 15000L))));

    mockMvc
        .perform(
            post("/travels/{travelId}/expenses", TRAVEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "저녁식사",
                      "amount": 30000,
                      "currency": "KRW",
                      "category": "FOOD",
                      "payerId": "%s",
                      "participantIds": ["%s", "%s"],
                      "spentAt": "2026-09-05T19:00:00",
                      "memo": "회식"
                    }
                    """
                        .formatted(PAYER_ID, USER_ID, PAYER_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.expenseId").value(EXPENSE_ID.toString()))
        .andExpect(jsonPath("$.splitType").value("EQUAL"))
        .andExpect(jsonPath("$.shares.length()").value(2));

    ArgumentCaptor<TravelExpenseCreateRequest> captor =
        ArgumentCaptor.forClass(TravelExpenseCreateRequest.class);
    verify(travelExpenseService).createEqualExpense(any(), eq(TRAVEL_ID), captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().payerId()).isEqualTo(PAYER_ID);
  }

  @Test
  @DisplayName("경비를 삭제하면 204를 반환한다")
  void deleteExpenseReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/travels/{travelId}/expenses/{expenseId}", TRAVEL_ID, EXPENSE_ID))
        .andExpect(status().isNoContent());

    verify(travelExpenseService).deleteExpense(any(), eq(TRAVEL_ID), eq(EXPENSE_ID));
  }

  @Test
  @DisplayName("인증되지 않은 사용자의 요청은 모두 UnauthenticatedException을 던진다")
  void rejectsUnauthenticatedUser() {
    assertThatThrownBy(() -> travelExpenseController.getExpenseSummary(null, TRAVEL_ID, null, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelExpenseController.deleteExpense(null, TRAVEL_ID, EXPENSE_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () -> travelExpenseController.updateExpense(null, TRAVEL_ID, EXPENSE_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelExpenseController.getExpense(null, TRAVEL_ID, EXPENSE_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () ->
                travelExpenseController.getExpenses(
                    null, TRAVEL_ID, null, null, null, null, null, Pageable.unpaged()))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelExpenseController.createEqualExpense(null, TRAVEL_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private TravelExpenseDetailResponse detailResponse() {
    return new TravelExpenseDetailResponse(
        EXPENSE_ID,
        TRAVEL_ID,
        "저녁식사",
        30000L,
        "KRW",
        ExpenseCategory.FOOD,
        new TravelExpenseDetailResponse.UserSummary(PAYER_ID, "결제자"),
        new TravelExpenseDetailResponse.UserSummary(USER_ID, "작성자"),
        ExpenseSplitType.EQUAL,
        LocalDateTime.of(2026, 9, 5, 19, 0),
        "회식",
        List.of(
            new TravelExpenseDetailResponse.ShareDetail(USER_ID, "작성자", 15000L),
            new TravelExpenseDetailResponse.ShareDetail(PAYER_ID, "결제자", 15000L)),
        true,
        LocalDateTime.of(2026, 9, 5, 19, 5),
        LocalDateTime.of(2026, 9, 5, 19, 5));
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
        return new AuthenticatedUser(USER_ID, "user@example.com", "tester", List.of());
      }
    };
  }
}
