package com.butingbe.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.place.dto.request.FestivalSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceKeywordSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceLocationSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.FestivalResDto;
import com.butingbe.domain.place.dto.response.FestivalSearchResDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
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
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("지역 코드로 장소 목록을 조회한다")
  void searchPlaces() throws Exception {
    given(placeService.searchPlaces(any(PlaceSearchReqDto.class)))
        .willReturn(
            new PlaceSearchResDto(
                2,
                10,
                1,
                List.of(
                    new PlaceResDto(
                        "12345", "39", "부산 돼지국밥", "부산진구", null, null, null, null, "26", "230"))));

    mockMvc
        .perform(
            get("/api/v1/places")
                .param("page", "2")
                .param("size", "10")
                .param("districtCode", "230")
                .param("contentTypeId", "39"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.places[0].contentId").value("12345"));
  }

  @Test
  @DisplayName("좌표와 반경으로 주변 장소를 조회한다")
  void searchPlacesByLocation() throws Exception {
    given(placeService.searchPlacesByLocation(any(PlaceLocationSearchReqDto.class)))
        .willReturn(
            new PlaceSearchResDto(
                1,
                20,
                1,
                List.of(
                    new PlaceResDto(
                        "67890",
                        "12",
                        "해운대 해수욕장",
                        "해운대구",
                        null,
                        null,
                        129.16,
                        35.158,
                        "26",
                        "260"))));

    mockMvc
        .perform(
            get("/api/v1/places/location")
                .param("mapX", "129.16")
                .param("mapY", "35.158")
                .param("radius", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places[0].title").value("해운대 해수욕장"));
  }

  @Test
  @DisplayName("반경이 허용 범위를 벗어나면 400을 반환한다")
  void searchPlacesByLocationRejectsRadiusOutOfRange() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/places/location")
                .param("mapX", "129.16")
                .param("mapY", "35.158")
                .param("radius", "20001"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("시작일 기준으로 축제를 조회한다")
  void searchFestivals() throws Exception {
    given(placeService.searchFestivals(any(FestivalSearchReqDto.class)))
        .willReturn(
            new FestivalSearchResDto(
                "20260901",
                "20260930",
                1,
                20,
                1,
                List.of(
                    new FestivalResDto(
                        "11111",
                        "15",
                        "부산불꽃축제",
                        "광안리",
                        null,
                        null,
                        129.11,
                        35.15,
                        "26",
                        "260",
                        "20260901",
                        "20260930"))));

    mockMvc
        .perform(get("/api/v1/places/festivals").param("eventStartDate", "20260901"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventStartDate").value("20260901"))
        .andExpect(jsonPath("$.festivals[0].title").value("부산불꽃축제"));
  }

  @Test
  @DisplayName("시작일 형식이 8자리 숫자가 아니면 400을 반환한다")
  void searchFestivalsRejectsMalformedStartDate() throws Exception {
    mockMvc
        .perform(get("/api/v1/places/festivals").param("eventStartDate", "2026-09-01"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("콘텐츠 ID로 장소 상세를 조회한다")
  void getPlaceDetail() throws Exception {
    given(placeService.getPlaceDetail("12345", "39", "부산 돼지국밥"))
        .willReturn(new PlaceDetailResDto("12345", "39", Map.of("overview", "부산 대표 국밥집"), null));

    mockMvc
        .perform(
            get("/api/v1/places/{contentId}/detail", "12345")
                .param("contentTypeId", "39")
                .param("googleSearchText", "부산 돼지국밥"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentId").value("12345"))
        .andExpect(jsonPath("$.details.overview").value("부산 대표 국밥집"));
  }

  @Test
  @DisplayName("googleSearchText 없이도 장소 상세를 조회할 수 있다")
  void getPlaceDetailWithoutGoogleSearchText() throws Exception {
    given(placeService.getPlaceDetail("12345", "39", null))
        .willReturn(new PlaceDetailResDto("12345", "39", Map.of(), null));

    mockMvc
        .perform(get("/api/v1/places/{contentId}/detail", "12345").param("contentTypeId", "39"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentTypeId").value("39"));
  }
}
