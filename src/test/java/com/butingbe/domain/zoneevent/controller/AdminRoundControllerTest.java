package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
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
        RoundType.REGULAR,
        RoundStatus.SCHEDULED,
        OffsetDateTime.now(),
        OffsetDateTime.now().plusDays(1),
        "Asia/Seoul",
        null,
        List.of(),
        List.of());
  }

  @Test
  @DisplayName("회차 생성 201")
  void create() throws Exception {
    when(consoleService.createRound(any(), any())).thenReturn(round());
    mockMvc
        .perform(
            post("/admin/zone-event-rounds")
                .contentType("application/json")
                .content(
                    "{\"startsAt\":\"2026-09-06T10:00:00+09:00\",\"endsAt\":\"2026-09-07T10:00:00+09:00\",\"zoneIds\":[\"YEONGDO\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.roundId").value(ROUND.toString()));
  }

  @Test
  @DisplayName("빈 구역 목록으로 생성하면 400")
  void createInvalid() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-rounds")
                .contentType("application/json")
                .content(
                    "{\"startsAt\":\"2026-09-06T10:00:00+09:00\",\"endsAt\":\"2026-09-07T10:00:00+09:00\",\"zoneIds\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("캘린더·상세·제안 200")
  void reads() throws Exception {
    when(consoleService.listRounds(any(), any(), any())).thenReturn(List.of(round()));
    when(consoleService.roundDetail(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.suggestSlots(any(), anyInt()))
        .thenReturn(new SlotSuggestionResDto(List.of("YEONGDO"), List.of("YEONGDO: 직전 2회차 미오픈")));

    mockMvc
        .perform(
            get("/admin/zone-event-rounds")
                .param("from", "2026-09-01T00:00:00+09:00")
                .param("to", "2026-09-30T00:00:00+09:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].roundId").value(ROUND.toString()));
    mockMvc.perform(get("/admin/zone-event-rounds/{id}", ROUND)).andExpect(status().isOk());
    mockMvc
        .perform(get("/admin/zone-event-rounds/suggest-slots").param("authSlots", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slots[0]").value("YEONGDO"));
  }

  @Test
  @DisplayName("슬롯 교체·예비 타겟·우천 교체")
  void mutations() throws Exception {
    when(consoleService.reassignSlot(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.addBackupTarget(any(), eq(ROUND), any())).thenReturn(round());
    when(consoleService.swapTarget(any(), eq(ROUND), any())).thenReturn(round());

    mockMvc
        .perform(
            patch("/admin/zone-event-rounds/{id}/slots", ROUND)
                .contentType("application/json")
                .content(
                    "{\"slotId\":\"55555555-0000-0000-0000-000000000001\",\"zoneId\":\"YEONGDO\"}"))
        .andExpect(status().isOk());
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
  @DisplayName("오픈·종료·정산·리포트")
  void lifecycle() throws Exception {
    when(consoleService.open(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.close(any(), eq(ROUND))).thenReturn(round());
    when(consoleService.settle(any(), eq(ROUND)))
        .thenReturn(Map.of("roundId", ROUND.toString(), "events", List.of()));
    when(consoleService.settlementReport(any(), eq(ROUND)))
        .thenReturn(Map.of("roundId", ROUND.toString()));

    mockMvc.perform(post("/admin/zone-event-rounds/{id}/open", ROUND)).andExpect(status().isOk());
    mockMvc.perform(post("/admin/zone-event-rounds/{id}/close", ROUND)).andExpect(status().isOk());
    mockMvc
        .perform(post("/admin/zone-event-rounds/{id}/settle", ROUND))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roundId").value(ROUND.toString()));
    mockMvc
        .perform(get("/admin/zone-event-rounds/{id}/settlement-report", ROUND))
        .andExpect(status().isOk());
    verify(consoleService).open(any(), eq(ROUND));
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
