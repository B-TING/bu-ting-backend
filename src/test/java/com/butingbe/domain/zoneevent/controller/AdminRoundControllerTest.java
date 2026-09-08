package com.butingbe.domain.zoneevent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.service.AdminRoundConsoleService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class AdminRoundControllerTest {

  private static final UUID ROUND = UUID.fromString("44444444-0000-0000-0000-000000000001");

  @Mock private AdminRoundConsoleService consoleService;
  @InjectMocks private AdminRoundController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.operator.forbidden", Locale.KOREAN, "운영 권한이 없습니다.");
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  private AdminRoundResDto round() {
    return new AdminRoundResDto(
        ROUND.toString(),
        1,
        "테스트 회차",
        RoundType.REGULAR,
        RoundStatus.DRAFT,
        OffsetDateTime.now(),
        OffsetDateTime.now().plusDays(1),
        "Asia/Seoul",
        null,
        null,
        false,
        null,
        null,
        0L,
        List.of(),
        List.of());
  }

  @Test
  @DisplayName("회차 초안 생성 201")
  void create() throws Exception {
    when(consoleService.createRound(any(), any())).thenReturn(round());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds")
                .contentType("application/json")
                .content(
                    "{\"roundNo\":1,\"name\":\"테스트 회차\",\"startsAt\":\"2026-09-06T10:00:00+09:00\",\"endsAt\":\"2026-09-07T10:00:00+09:00\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.roundId").value(ROUND.toString()))
        .andExpect(jsonPath("$.data.status").value("DRAFT"));
  }

  @Test
  @DisplayName("roundNo 없이 생성하면 400")
  void createInvalid() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-rounds")
                .contentType("application/json")
                .content(
                    "{\"startsAt\":\"2026-09-06T10:00:00+09:00\",\"endsAt\":\"2026-09-07T10:00:00+09:00\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("목록·상세·제안 200")
  void reads() throws Exception {
    when(consoleService.listRounds(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminRoundPageResDto(List.of(round()), 0, 20, 1, 1));
    when(consoleService.roundDetail(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.suggestSlots(any(), anyInt()))
        .thenReturn(new SlotSuggestionResDto(List.of("YEONGDO"), List.of("YEONGDO: 직전 2회차 미오픈")));

    mockMvc
        .perform(
            get("/admin/zone-event-rounds")
                .param("status", "DRAFT")
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].roundId").value(ROUND.toString()))
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mockMvc.perform(get("/admin/zone-event-rounds/{id}", ROUND)).andExpect(status().isOk());
    mockMvc
        .perform(get("/admin/zone-event-rounds/suggest-slots").param("authSlots", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slots[0]").value("YEONGDO"));
  }

  @Test
  @DisplayName("PATCH·schedule·cancel")
  void mutations() throws Exception {
    when(consoleService.patch(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.schedule(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.cancel(any(), eq(ROUND), any())).thenReturn(round());

    mockMvc
        .perform(
            patch("/admin/zone-event-rounds/{id}", ROUND)
                .contentType("application/json")
                .content("{\"expectedRevision\":0,\"name\":\"새 이름\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/admin/zone-event-rounds/{id}/schedule", ROUND))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds/{id}/cancel", ROUND)
                .contentType("application/json")
                .content("{\"reason\":\"우천\",\"expectedRevision\":0}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("예비 타겟·우천 교체")
  void slotMutations() throws Exception {
    when(consoleService.addBackupTarget(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.swapTarget(any(), eq(ROUND), any())).thenReturn(round());

    mockMvc
        .perform(
            post("/admin/zone-event-rounds/{id}/backup-targets", ROUND)
                .contentType("application/json")
                .content(
                    "{\"targetKind\":\"PLACE\",\"placeName\":\"대체지\",\"latitude\":35.1,\"longitude\":129.1,\"radiusM\":80}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds/{id}/swap-target", ROUND)
                .contentType("application/json")
                .content(
                    "{\"eventId\":\"66666666-0000-0000-0000-000000000001\",\"backupTargetId\":\"77777777-0000-0000-0000-000000000001\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("정산·리포트")
  void settlement() throws Exception {
    when(consoleService.settle(any(), eq(ROUND)))
        .thenReturn(Map.of("roundId", ROUND.toString(), "events", List.of()));
    when(consoleService.settlementReport(any(), eq(ROUND)))
        .thenReturn(Map.of("roundId", ROUND.toString()));

    mockMvc.perform(post("/admin/zone-event-rounds/{id}/settle", ROUND)).andExpect(status().isOk());
    mockMvc
        .perform(get("/admin/zone-event-rounds/{id}/settlement-report", ROUND))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("open/close 엔드포인트는 더 이상 존재하지 않는다")
  void openCloseRemoved() {
    for (var method : controller.getClass().getMethods()) {
      assertThat(method.getName()).isNotIn("open", "close");
    }
  }

  @Test
  @DisplayName("운영 권한 없으면 403")
  void forbidden() throws Exception {
    when(consoleService.roundDetail(any(), eq(ROUND)))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));
    mockMvc.perform(get("/admin/zone-event-rounds/{id}", ROUND)).andExpect(status().isForbidden());
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
        return new AuthenticatedUser(ROUND, "op@example.com", "op", List.of());
      }
    };
  }
}
