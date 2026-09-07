package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AlbumItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AlbumPageResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventAlbumService;
import java.time.OffsetDateTime;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ZoneEventAlbumControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID PARTICIPATION_ID =
      UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private ZoneEventAlbumService albumService;
  @InjectMocks private ZoneEventAlbumController controller;

  private MockMvc mockMvc;
  private AuthenticatedUser currentUser;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .build();
  }

  @Test
  @DisplayName("비로그인 이벤트 앨범을 200으로 반환한다")
  void eventAlbumAnonymous() throws Exception {
    currentUser = null;
    when(albumService.eventAlbum(eq(EVENT_ID), any(), isNull(), any(), isNull()))
        .thenReturn(new AlbumPageResDto(List.of(item()), "next", true));

    mockMvc
        .perform(get("/zone-events/{eventId}/album", EVENT_ID).param("sort", "LATEST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].participationId").value(PARTICIPATION_ID.toString()))
        .andExpect(jsonPath("$.data.hasNext").value(true));
  }

  @Test
  @DisplayName("구역·회차 앨범도 200으로 반환한다")
  void zoneAndRoundAlbum() throws Exception {
    currentUser = null;
    UUID roundId = UUID.randomUUID();
    when(albumService.zoneAlbum(eq("SUYEONG_NAMGU"), any(), any(), any(), isNull()))
        .thenReturn(new AlbumPageResDto(List.of(), null, false));
    when(albumService.roundAlbum(eq(roundId), any(), any(), any(), isNull()))
        .thenReturn(new AlbumPageResDto(List.of(), null, false));

    mockMvc.perform(get("/zones/{zoneId}/album", "SUYEONG_NAMGU")).andExpect(status().isOk());
    mockMvc.perform(get("/zone-event-rounds/{roundId}/album", roundId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("로그인 뷰어의 id를 서비스에 전달한다")
  void passesViewerId() throws Exception {
    currentUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    when(albumService.eventAlbum(eq(EVENT_ID), any(), any(), any(), eq(USER_ID)))
        .thenReturn(new AlbumPageResDto(List.of(), null, false));

    mockMvc.perform(get("/zone-events/{eventId}/album", EVENT_ID)).andExpect(status().isOk());

    verify(albumService).eventAlbum(eq(EVENT_ID), any(), any(), any(), eq(USER_ID));
  }

  @Test
  @DisplayName("공개 설정 변경은 200을 반환한다")
  void setVisibility() throws Exception {
    currentUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());

    mockMvc
        .perform(
            patch("/zone-event-participations/{id}/visibility", PARTICIPATION_ID)
                .contentType("application/json")
                .content("{\"visibility\":\"PRIVATE\"}"))
        .andExpect(status().isOk());

    verify(albumService).setVisibility(any(), eq(PARTICIPATION_ID), eq("PRIVATE"));
  }

  @Test
  @DisplayName("visibility가 없으면 400이다")
  void setVisibilityMissing() throws Exception {
    currentUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());

    mockMvc
        .perform(
            patch("/zone-event-participations/{id}/visibility", PARTICIPATION_ID)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  private AlbumItemResDto item() {
    return new AlbumItemResDto(
        PARTICIPATION_ID.toString(),
        EVENT_ID.toString(),
        "광안대교 야경",
        "SUYEONG_NAMGU",
        USER_ID.toString(),
        "author",
        null,
        null,
        "후기",
        "https://signed/media.jpg",
        3600,
        24,
        false,
        3,
        false,
        OffsetDateTime.now());
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
        return currentUser;
      }
    };
  }
}
