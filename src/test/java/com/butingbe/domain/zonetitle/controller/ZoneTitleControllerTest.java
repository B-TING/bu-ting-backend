package com.butingbe.domain.zonetitle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import com.butingbe.domain.zonetitle.dto.response.MyZoneTitlesResDto;
import com.butingbe.domain.zonetitle.dto.response.ZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ZoneTitleControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID TITLE_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");

  @Mock private ZoneTitleService zoneTitleService;
  @InjectMocks private ZoneTitleController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  @DisplayName("내 칭호를 200으로 반환한다")
  void myTitles() throws Exception {
    when(zoneTitleService.myTitles(any()))
        .thenReturn(new MyZoneTitlesResDto(equipped(), null, List.of()));

    mockMvc
        .perform(get("/users/me/zone-titles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.equipped.titleCode").value("SUYEONG_NAMGU_T2"));
  }

  @Test
  @DisplayName("대표 칭호 장착 200 / 해제 204")
  void equipAndUnequip() throws Exception {
    when(zoneTitleService.equip(any(), eq(TITLE_ID))).thenReturn(equipped());

    mockMvc
        .perform(patch("/users/me/zone-titles/{id}/equip", TITLE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tier").value(2));
    mockMvc.perform(delete("/users/me/zone-titles/equipped")).andExpect(status().isNoContent());
    verify(zoneTitleService).unequip(any());
  }

  @Test
  @DisplayName("칭호 정의 전체를 200으로 반환한다")
  void allDefs() throws Exception {
    when(zoneTitleService.allDefs())
        .thenReturn(
            List.of(
                new ZoneTitleDefResDto(
                    "SUYEONG_NAMGU", 1, 1, "SUYEONG_NAMGU_T1", "발자국", "chip", "#A78BFA")));

    mockMvc
        .perform(get("/zone-titles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].titleCode").value("SUYEONG_NAMGU_T1"));
  }

  private EquippedTitleResDto equipped() {
    return new EquippedTitleResDto(
        "SUYEONG_NAMGU_T2", "광안 러버", "SUYEONG_NAMGU", 2, "chip_text", "#8B5CF6");
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
