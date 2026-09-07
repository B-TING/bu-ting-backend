package com.butingbe.domain.zoneevent.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.zoneevent.dto.response.RoundStatusResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.service.RoundStatusQueryService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class ZoneEventRoundControllerTest {

  @Mock private RoundStatusQueryService roundStatusQueryService;
  @InjectMocks private ZoneEventRoundController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.zone_event.not_found", Locale.KOREAN, "회차를 찾을 수 없습니다.");
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("회차 현황 200")
  void current() throws Exception {
    when(roundStatusQueryService.current())
        .thenReturn(
            new RoundStatusResDto(
                "r1",
                RoundStatus.OPEN,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusDays(1),
                List.of(new RoundStatusResDto.ZoneSlot("SUYEONG_NAMGU", "OPEN", "e1"))));
    mockMvc
        .perform(get("/zone-event-rounds/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.zones[0].slotStatus").value("OPEN"));
  }

  @Test
  @DisplayName("열린·예정 회차가 없으면 404")
  void notFound() throws Exception {
    when(roundStatusQueryService.current())
        .thenThrow(new ResourceNotFoundException("error.zone_event.not_found"));
    mockMvc.perform(get("/zone-event-rounds/current")).andExpect(status().isNotFound());
  }
}
