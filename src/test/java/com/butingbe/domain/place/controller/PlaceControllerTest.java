package com.butingbe.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.place.dto.request.PlaceKeywordSearchReqDto;
import com.butingbe.domain.place.dto.response.PlaceResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import com.butingbe.domain.place.exception.PlaceKeywordNotFoundException;
import com.butingbe.domain.place.service.PlaceService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

  private MockMvc mockMvc;

  @Mock private PlaceService placeService;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");

    AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
    localeResolver.setDefaultLocale(Locale.KOREAN);

    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    mockMvc =
        MockMvcBuilders.standaloneSetup(new PlaceController(placeService))
            .setControllerAdvice(new GlobalExceptionHandler(messageSource, localeResolver))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setCustomHandlerMapping(this::apiPrefixHandlerMapping)
            .build();
  }

  private RequestMappingHandlerMapping apiPrefixHandlerMapping() {
    RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
    handlerMapping.setPathPrefixes(
        Map.of(
            "/api/v1",
            HandlerTypePredicate.forAnnotation(RestController.class)
                .and(HandlerTypePredicate.forBasePackage("com.butingbe.domain"))));
    return handlerMapping;
  }

  @Test
  @DisplayName("키워드로 장소를 검색한다")
  void searchPlacesByKeyword() throws Exception {
    given(placeService.searchPlacesByKeyword(any(PlaceKeywordSearchReqDto.class)))
        .willReturn(
            new PlaceSearchResDto(
                1,
                20,
                1,
                List.of(
                    new PlaceResDto(
                        "12345", "39", "부산 돼지국밥", "부산진구", null, null, null, null, "26", "230"))));

    mockMvc
        .perform(get("/api/v1/places/search").param("keyword", "해운대"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.places[0].title").value("부산 돼지국밥"));
  }

  @Test
  @DisplayName("공백 키워드는 검색 요청으로 허용하지 않는다")
  void searchPlacesByKeywordRejectsBlankKeyword() throws Exception {
    mockMvc
        .perform(get("/api/v1/places/search").param("keyword", "   "))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("키워드 검색 결과가 없으면 404를 반환한다")
  void searchPlacesByKeywordReturnsNotFoundWhenNoPlacesFound() throws Exception {
    given(placeService.searchPlacesByKeyword(any(PlaceKeywordSearchReqDto.class)))
        .willThrow(new PlaceKeywordNotFoundException());

    mockMvc
        .perform(get("/api/v1/places/search").locale(Locale.KOREAN).param("keyword", "없는장소"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("검색어와 일치하는 장소를 찾을 수 없습니다."));
  }
}
