package com.butingbe.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.dto.request.OAuthLoginReqDto;
import com.butingbe.domain.auth.service.OAuthLoginService;
import com.butingbe.domain.user.dto.response.OAuth2LoginResDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class AuthControllerTest {

  private MockMvc mockMvc;

  @Mock private OAuthLoginService oAuthLoginService;
  @Mock private com.butingbe.domain.auth.service.OpaqueTokenService opaqueTokenService;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");

    AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
    localeResolver.setDefaultLocale(Locale.KOREAN);
    localeResolver.setSupportedLocales(
        List.of(Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE));

    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(oAuthLoginService, opaqueTokenService))
            .setControllerAdvice(new GlobalExceptionHandler(messageSource, localeResolver))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setCustomHandlerMapping(this::apiPrefixHandlerMapping)
            .apply(documentationConfiguration(restDocumentation))
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
  @DisplayName("클라이언트 SSO provider token으로 로그인하면 opaque token을 발급한다")
  void loginWithOAuth() throws Exception {
    given(oAuthLoginService.login(any(OAuthLoginReqDto.class), any()))
        .willReturn(
            new OAuth2LoginResDto(
                "550e8400-e29b-41d4-a716-446655440000",
                "oauth@example.com",
                "소셜유저",
                "google",
                true,
                false,
                "opaque-token",
                "Bearer",
                3600,
                "refresh-token",
                2592000));

    mockMvc
        .perform(
            post("/api/v1/auth/oauth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer existing-opaque-token")
                .content(
                    """
                    {
                      "provider": "google",
                      "providerToken": "GOOGLE_AUTHORIZATION_CODE"
                    }
                    """))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.emailRequired").value(false))
        .andExpect(jsonPath("$.data.accessToken").value("opaque-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andDo(
            document(
                "auth-oauth-login",
                requestHeaders(
                    headerWithName(HttpHeaders.AUTHORIZATION)
                        .optional()
                        .description("기존 B-TING opaque token. 같은 사용자이고 만료 전이면 재사용됩니다.")),
                requestFields(
                    fieldWithPath("provider").description("SSO provider: google, naver, kakao"),
                    fieldWithPath("providerToken")
                        .description(
                            "웹 클라이언트는 OAuth authorization code를 전달하고, 앱 클라이언트는 Google/Kakao id_token을 전달합니다."),
                    fieldWithPath("redirectUri")
                        .optional()
                        .type(JsonFieldType.STRING)
                        .description("Authorization code 발급 시 사용한 redirect_uri"),
                    fieldWithPath("codeVerifier")
                        .optional()
                        .type(JsonFieldType.STRING)
                        .description("PKCE authorization code flow에서 사용한 code_verifier")),
                responseFields(
                    fieldWithPath("success").description("요청 성공 여부"),
                    fieldWithPath("message").description("응답 메시지"),
                    fieldWithPath("data.userId").description("B-TING 사용자 ID"),
                    fieldWithPath("data.email").description("사용자 이메일"),
                    fieldWithPath("data.nickname").description("사용자 닉네임"),
                    fieldWithPath("data.provider").description("연결된 SSO provider"),
                    fieldWithPath("data.loggedIn").description("로그인 성공 여부"),
                    fieldWithPath("data.emailRequired")
                        .description("이메일 추가 입력 필요 여부. OAuth 로그인에서는 provider 이메일 제공이 필수이므로 false"),
                    fieldWithPath("data.accessToken").description("B-TING opaque access token"),
                    fieldWithPath("data.tokenType").description("Bearer"),
                    fieldWithPath("data.expiresIn").description("액세스 토큰 만료까지 남은 시간, 초 단위"),
                    fieldWithPath("data.refreshToken")
                        .description("액세스 토큰 재발급에 쓰는 토큰. 이 토큰으로 API를 호출할 수는 없다"),
                    fieldWithPath("data.refreshExpiresIn")
                        .description("리프레시 토큰 만료까지 남은 시간, 초 단위"))));
  }

  @Test
  @DisplayName("리프레시 토큰으로 액세스 토큰을 재발급하고 리프레시도 함께 갈아끼운다")
  void refreshToken() throws Exception {
    given(opaqueTokenService.refresh("refresh-token"))
        .willReturn(
            new com.butingbe.domain.auth.service.OpaqueTokenService.IssuedOpaqueToken(
                "new-access-token",
                "Bearer",
                3600,
                java.time.LocalDateTime.now().plusHours(1),
                "new-refresh-token",
                2592000));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
        .andDo(
            document(
                "auth-refresh",
                requestFields(fieldWithPath("refreshToken").description("로그인 때 받은 리프레시 토큰")),
                responseFields(
                    fieldWithPath("success").description("성공 여부"),
                    fieldWithPath("message").description("결과 메시지"),
                    fieldWithPath("data.accessToken").description("새 액세스 토큰"),
                    fieldWithPath("data.tokenType").description("Bearer"),
                    fieldWithPath("data.expiresIn").description("액세스 토큰 만료까지 남은 시간, 초 단위"),
                    fieldWithPath("data.refreshToken")
                        .description("새 리프레시 토큰. 이전 리프레시는 이 시점에 폐기된다"),
                    fieldWithPath("data.refreshExpiresIn")
                        .description("리프레시 토큰 만료까지 남은 시간, 초 단위"))));
  }

  @Test
  @DisplayName("리프레시 토큰이 비면 400이다")
  void refreshRejectsBlankToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Accept-Language가 en이면 검증 실패 메시지를 영어로 응답한다")
  void loginValidationFailWithEnglishLocale() throws Exception {
    assertLocalizedValidationMessage("en", "OAuth provider is required.");
  }

  @Test
  @DisplayName("Accept-Language가 ja이면 검증 실패 메시지를 일본어로 응답한다")
  void loginValidationFailWithJapaneseLocale() throws Exception {
    assertLocalizedValidationMessage("ja", "OAuthプロバイダーは必須入力項目です。");
  }

  @Test
  @DisplayName("Accept-Language가 zh이면 검증 실패 메시지를 중국어로 응답한다")
  void loginValidationFailWithChineseLocale() throws Exception {
    assertLocalizedValidationMessage("zh", "OAuth provider为必填项。");
  }

  /** provider를 비워 @Valid를 실패시키고, 응답 메시지가 Accept-Language를 따르는지 본다. */
  private void assertLocalizedValidationMessage(String language, String expectedMessage)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/oauth/login")
                .header("Accept-Language", language)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "provider": "",
                      "providerToken": "token"
                    }
                    """))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }
}
