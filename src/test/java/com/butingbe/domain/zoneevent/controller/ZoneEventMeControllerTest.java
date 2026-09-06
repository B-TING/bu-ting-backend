package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryPageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.service.ZoneEventParticipationQueryService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class ZoneEventMeControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private ZoneEventParticipationQueryService queryService;
  @InjectMocks private ZoneEventMeController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage(
        "error.zone_event.participation.invalid_state", Locale.KOREAN, "상태가 올바르지 않습니다.");
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("내 참여 이력을 커서 페이징으로 반환한다")
  void history() throws Exception {
    when(queryService.history(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ParticipationHistoryPageResDto(List.of(), "next-cursor", true));

    mockMvc
        .perform(get("/users/me/zone-event-participations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
        .andExpect(jsonPath("$.data.hasNext").value(true));
  }

  @Test
  @DisplayName("상태 필터를 콤마로 파싱해 서비스에 전달한다")
  void parsesStatusFilter() throws Exception {
    when(queryService.history(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ParticipationHistoryPageResDto(List.of(), null, false));

    mockMvc
        .perform(get("/users/me/zone-event-participations").param("status", "SUCCESS,CANCELLED"))
        .andExpect(status().isOk());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ParticipationStatus>> captor = ArgumentCaptor.forClass(List.class);
    verify(queryService).history(any(), any(), any(), captor.capture(), any(), any(), any(), any());
    org.assertj.core.api.Assertions.assertThat(captor.getValue())
        .containsExactly(ParticipationStatus.SUCCESS, ParticipationStatus.CANCELLED);
  }

  @Test
  @DisplayName("잘못된 상태 필터는 400이다")
  void invalidStatusFilter() throws Exception {
    mockMvc
        .perform(get("/users/me/zone-event-participations").param("status", "NOPE"))
        .andExpect(status().isBadRequest());
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
