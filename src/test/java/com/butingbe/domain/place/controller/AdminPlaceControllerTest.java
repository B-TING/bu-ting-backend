package com.butingbe.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.place.dto.request.PlaceCurationReqDto;
import com.butingbe.domain.place.dto.response.PlaceCurationResDto;
import com.butingbe.domain.place.dto.response.PlaceEnrichResDto;
import com.butingbe.domain.place.dto.response.PlaceSyncResDto;
import com.butingbe.domain.place.entity.PlaceTimeSlot;
import com.butingbe.domain.place.service.PlaceCurationService;
import com.butingbe.domain.place.service.PlaceEnrichmentService;
import com.butingbe.domain.place.service.PlaceSyncService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
import java.util.List;
import java.util.Locale;
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
class AdminPlaceControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private PlaceSyncService placeSyncService;
  @Mock private PlaceEnrichmentService placeEnrichmentService;
  @Mock private PlaceCurationService placeCurationService;
  @InjectMocks private AdminPlaceController controller;

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

  @Test
  @DisplayName("동기화는 200과 적재 요약을 반환한다")
  void sync() throws Exception {
    when(placeSyncService.sync(any())).thenReturn(new PlaceSyncResDto(120, 30, 2, 1, 152));

    mockMvc
        .perform(post("/admin/places/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.created").value(120))
        .andExpect(jsonPath("$.data.updated").value(30))
        .andExpect(jsonPath("$.data.skipped").value(2))
        .andExpect(jsonPath("$.data.failedPages").value(1))
        .andExpect(jsonPath("$.data.totalCount").value(152));
  }

  @Test
  @DisplayName("운영 권한이 없으면 403이다")
  void syncForbidden() throws Exception {
    when(placeSyncService.sync(any()))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));

    mockMvc.perform(post("/admin/places/sync")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("인기도 보강은 200과 요약을 반환한다")
  void enrich() throws Exception {
    when(placeEnrichmentService.enrich(any(), eq(20)))
        .thenReturn(new PlaceEnrichResDto(20, 18, 1, 1));

    mockMvc
        .perform(post("/admin/places/enrich").param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targeted").value(20))
        .andExpect(jsonPath("$.data.enriched").value(18))
        .andExpect(jsonPath("$.data.withoutRating").value(1))
        .andExpect(jsonPath("$.data.failed").value(1));
  }

  @Test
  @DisplayName("limit 없이 호출하면 서비스 기본값을 쓴다")
  void enrichWithoutLimit() throws Exception {
    when(placeEnrichmentService.enrich(any(), eq(null)))
        .thenReturn(new PlaceEnrichResDto(50, 50, 0, 0));

    mockMvc
        .perform(post("/admin/places/enrich"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targeted").value(50));
  }

  @Test
  @DisplayName("보정은 200과 보정된 장소를 반환한다")
  void curate() throws Exception {
    UUID placeId = UUID.fromString("44444444-0000-0000-0000-000000000001");
    when(placeCurationService.curate(any(), eq(placeId), any(PlaceCurationReqDto.class)))
        .thenReturn(new PlaceCurationResDto(placeId, "광안리해수욕장", "12", 120, PlaceTimeSlot.EVENING));

    mockMvc
        .perform(
            patch("/admin/places/{placeId}", placeId)
                .contentType("application/json")
                .content("{\"dwellMinutes\":120,\"preferredTimeSlot\":\"EVENING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dwellMinutes").value(120))
        .andExpect(jsonPath("$.data.preferredTimeSlot").value("EVENING"));
  }

  @Test
  @DisplayName("체류 시간이 허용 범위를 벗어나면 400이다")
  void curateInvalidDwellMinutes() throws Exception {
    UUID placeId = UUID.fromString("44444444-0000-0000-0000-000000000001");

    mockMvc
        .perform(
            patch("/admin/places/{placeId}", placeId)
                .contentType("application/json")
                .content("{\"dwellMinutes\":1}"))
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
        return new AuthenticatedUser(USER_ID, "admin@example.com", "admin", List.of());
      }
    };
  }
}
