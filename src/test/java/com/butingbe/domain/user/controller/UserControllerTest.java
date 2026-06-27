package com.butingbe.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.service.UserService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class UserControllerTest {

  private MockMvc mockMvc;

  @Mock private UserService userService;

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
        MockMvcBuilders.standaloneSetup(new UserController(userService))
            .setControllerAdvice(new GlobalExceptionHandler(messageSource, localeResolver))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setCustomHandlerMapping(this::apiPrefixHandlerMapping)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
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

  private RequestPostProcessor authenticated(UsernamePasswordAuthenticationToken authentication) {
    return request -> {
      SecurityContextHolder.getContext().setAuthentication(authentication);
      return request;
    };
  }

  // ==========================================
  // 👤 SIGN UP (회원가입) TEST
  // ==========================================

  @Test
  @DisplayName("올바른 회원가입 데이터가 JSON 형태로 들어오면 201 Created를 반환한다")
  void signUpSuccess() throws Exception {
    // given
    doNothing().when(userService).signUp(any(SignUpReqDto.class));
    AuthenticatedUser principal =
        new AuthenticatedUser(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "test@example.com",
            "테스터",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());

    // when & then
    mockMvc
        .perform(
            post("/api/v1/users/signup")
                .with(authenticated(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .content(
                    """
                                {
                                  "email": "test@example.com",
                                  "nickname": "테스터",
                                  "provider": "google",
                                  "providerId": "google-123",
                                  "firstName": "길동",
                                  "lastName": "홍"
                                }
                                """))
        .andDo(print())
        .andExpect(status().isCreated())
        .andDo(
            document(
                "users-sign-up",
                requestHeaders(
                    headerWithName(HttpHeaders.AUTHORIZATION)
                        .description("B-TING opaque token. Bearer 형식으로 전달합니다.")),
                requestFields(
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"),
                    fieldWithPath("provider").description("OAuth2 provider"),
                    fieldWithPath("providerId").description("OAuth2 provider user id"),
                    fieldWithPath("firstName").description("이름"),
                    fieldWithPath("lastName").description("성"))));
  }

  @Test
  @DisplayName("회원가입 요청 시 인증 토큰이 없으면 401 Unauthorized를 반환한다")
  void signUpFailWithoutAuthentication() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "test@example.com",
                                  "nickname": "테스터",
                                  "provider": "google",
                                  "providerId": "google-123",
                                  "firstName": "길동",
                                  "lastName": "홍"
                                }
                                """))
        .andDo(print())
        .andExpect(status().isUnauthorized());

    verify(userService, never()).signUp(any(SignUpReqDto.class));
  }

  @Test
  @DisplayName("회원가입 요청 시 이메일 형식이 누락되거나 잘못되면 @Valid에 의해 400 Bad Request를 뱉는다")
  void signUpValidationFail() throws Exception {
    // when & then
    mockMvc
        .perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "not-email-format",
                                  "nickname": "",
                                  "firstName": "",
                                  "lastName": ""
                                }
                                """))
        .andDo(print())
        .andExpect(status().isBadRequest());

    verify(userService, never()).signUp(any(SignUpReqDto.class));
  }

  @Test
  @DisplayName("Accept-Language가 en이면 검증 실패 메시지를 영어로 응답한다")
  void signUpValidationFailWithEnglishLocale() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/signup")
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "",
                                  "nickname": "tester",
                                  "firstName": "",
                                  "lastName": ""
                                }
                                """))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Email is required."));

    verify(userService, never()).signUp(any(SignUpReqDto.class));
  }

  @Test
  @DisplayName("Accept-Language가 ja이면 검증 실패 메시지를 일본어로 응답한다")
  void signUpValidationFailWithJapaneseLocale() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/signup")
                .header("Accept-Language", "ja")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "",
                                  "nickname": "tester",
                                  "firstName": "",
                                  "lastName": ""
                                }
                                """))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("メールアドレスは必須入力項目です。"));

    verify(userService, never()).signUp(any(SignUpReqDto.class));
  }

  @Test
  @DisplayName("Accept-Language가 zh이면 검증 실패 메시지를 중국어로 응답한다")
  void signUpValidationFailWithChineseLocale() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/signup")
                .header("Accept-Language", "zh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "email": "",
                                  "nickname": "tester",
                                  "firstName": "",
                                  "lastName": ""
                                }
                                """))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("邮箱为必填项。"));

    verify(userService, never()).signUp(any(SignUpReqDto.class));
  }

  // ==========================================
  // 🔑 SIGN IN (로그인/조회) TEST
  // ==========================================

  @Test
  @DisplayName("정상적인 이메일 파라미터로 로그인 요청 시 유저 정보를 JSON 규격으로 응답한다")
  void signInSuccess() throws Exception {
    // given
    String email = "success@example.com";
    UserResDto mockResponse = new UserResDto(email, "홍길동");
    given(userService.signIn(email)).willReturn(mockResponse);

    // when & then
    mockMvc
        .perform(
            get("/api/v1/users/signin")
                .param("email", email)
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.nickname").value("홍길동"))
        .andDo(
            document(
                "users-sign-in",
                queryParameters(parameterWithName("email").description("조회할 사용자 이메일")),
                responseFields(
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"))));
  }
}
