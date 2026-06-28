package com.butingbe.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.request.UpdateMyProfileReqDto;
import com.butingbe.domain.user.dto.response.MyProfileResDto;
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

  private UsernamePasswordAuthenticationToken userAuthentication(UUID userId) {
    AuthenticatedUser principal =
        new AuthenticatedUser(
            userId, "test@example.com", "테스터", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
  }

  // ==========================================
  // 👤 SIGN UP (회원가입) TEST
  // ==========================================

  @Test
  @DisplayName("올바른 회원가입 데이터가 JSON 형태로 들어오면 201 Created를 반환한다")
  void signUpSuccess() throws Exception {
    // given
    doNothing().when(userService).signUp(any(SignUpReqDto.class));
    UsernamePasswordAuthenticationToken authentication =
        userAuthentication(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

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
                requestFields(
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"),
                    fieldWithPath("provider").description("OAuth2 provider"),
                    fieldWithPath("providerId").description("OAuth2 provider user id"),
                    fieldWithPath("firstName").description("이름"),
                    fieldWithPath("lastName").description("성"))));
  }

  // ==========================================
  // 🙋 MY PROFILE TEST
  // ==========================================

  @Test
  @DisplayName("인증된 사용자는 내 프로필 정보를 조회할 수 있다")
  void getMyProfileSuccess() throws Exception {
    // given
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    given(userService.getMyProfile(any(AuthenticatedUser.class)))
        .willReturn(
            new MyProfileResDto(
                userId.toString(),
                "test@example.com",
                "테스터",
                "https://example.com/profile.png",
                "google",
                "길동",
                "홍"));

    // when & then
    mockMvc
        .perform(
            get("/api/v1/users/me")
                .with(authenticated(userAuthentication(userId)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.nickname").value("테스터"))
        .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"))
        .andDo(
            document(
                "users-me-get",
                responseFields(
                    fieldWithPath("userId").description("사용자 ID"),
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"),
                    fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                    fieldWithPath("provider").description("OAuth2 provider"),
                    fieldWithPath("firstName").description("이름"),
                    fieldWithPath("lastName").description("성"))));
  }

  @Test
  @DisplayName("개발 관리자 토큰으로 내 프로필 정보를 조회할 수 있다")
  void getMyProfileSuccessWithDevelopmentAdmin() throws Exception {
    // given
    AuthenticatedUser principal = AuthenticatedUser.developmentAdmin();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    UUID createdAdminId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    given(userService.getMyProfile(any(AuthenticatedUser.class)))
        .willReturn(
            new MyProfileResDto(
                createdAdminId.toString(),
                principal.email(),
                principal.nickname(),
                null,
                "development",
                "관리자",
                "개발"));

    // when & then
    mockMvc
        .perform(
            get("/api/v1/users/me")
                .with(authenticated(authentication))
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(createdAdminId.toString()))
        .andExpect(jsonPath("$.email").value("admin@local.dev"))
        .andExpect(jsonPath("$.nickname").value("개발 관리자"))
        .andExpect(jsonPath("$.provider").value("development"));
  }

  @Test
  @DisplayName("인증된 사용자는 내 회원 정보를 수정할 수 있다")
  void updateMyProfileSuccess() throws Exception {
    // given
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    given(
            userService.updateMyProfile(
                any(AuthenticatedUser.class), any(UpdateMyProfileReqDto.class)))
        .willReturn(
            new MyProfileResDto(
                userId.toString(),
                "test@example.com",
                "수정닉",
                "https://example.com/updated.png",
                "google",
                "길동",
                "홍"));

    // when & then
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(authenticated(userAuthentication(userId)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .content(
                    """
                                {
                                  "nickname": "수정닉",
                                  "profileImageUrl": "https://example.com/updated.png",
                                  "firstName": "길동",
                                  "lastName": "홍"
                                }
                                """))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("수정닉"))
        .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/updated.png"))
        .andDo(
            document(
                "users-me-update",
                requestFields(
                    fieldWithPath("nickname").optional().description("닉네임"),
                    fieldWithPath("profileImageUrl").optional().description("프로필 이미지 URL"),
                    fieldWithPath("firstName").optional().description("이름"),
                    fieldWithPath("lastName").optional().description("성")),
                responseFields(
                    fieldWithPath("userId").description("사용자 ID"),
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"),
                    fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                    fieldWithPath("provider").description("OAuth2 provider"),
                    fieldWithPath("firstName").description("이름"),
                    fieldWithPath("lastName").description("성"))));
  }

  // ==========================================
  // 🙋 MY PROFILE TEST
  // ==========================================

  @Test
  @DisplayName("인증된 사용자는 내 프로필 정보를 조회할 수 있다")
  void getMyProfileSuccess() throws Exception {
    // given
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    given(userService.getMyProfile(any(AuthenticatedUser.class)))
        .willReturn(
            new MyProfileResDto(
                userId.toString(),
                "test@example.com",
                "테스터",
                "https://example.com/profile.png",
                "google",
                "길동",
                "홍"));

    // when & then
    mockMvc
        .perform(
            get("/api/v1/users/me")
                .with(authenticated(userAuthentication(userId)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.nickname").value("테스터"))
        .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"))
        .andDo(
            document(
                "users-me-get",
                responseFields(
                    fieldWithPath("userId").description("사용자 ID"),
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"),
                    fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                    fieldWithPath("provider").description("OAuth2 provider"),
                    fieldWithPath("firstName").description("이름"),
                    fieldWithPath("lastName").description("성"))));
  }

  @Test
  @DisplayName("개발 관리자 토큰으로 내 프로필 정보를 조회할 수 있다")
  void getMyProfileSuccessWithDevelopmentAdmin() throws Exception {
    // given
    AuthenticatedUser principal = AuthenticatedUser.developmentAdmin();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    UUID createdAdminId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    given(userService.getMyProfile(any(AuthenticatedUser.class)))
        .willReturn(
            new MyProfileResDto(
                createdAdminId.toString(),
                principal.email(),
                principal.nickname(),
                null,
                "development",
                "관리자",
                "개발"));

    // when & then
    mockMvc
        .perform(
            get("/api/v1/users/me")
                .with(authenticated(authentication))
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(createdAdminId.toString()))
        .andExpect(jsonPath("$.email").value("admin@local.dev"))
        .andExpect(jsonPath("$.nickname").value("개발 관리자"))
        .andExpect(jsonPath("$.provider").value("development"));
  }

  @Test
  @DisplayName("인증된 사용자는 내 회원 정보를 수정할 수 있다")
  void updateMyProfileSuccess() throws Exception {
    // given
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    given(
            userService.updateMyProfile(
                any(AuthenticatedUser.class), any(UpdateMyProfileReqDto.class)))
        .willReturn(
            new MyProfileResDto(
                userId.toString(),
                "test@example.com",
                "수정닉",
                "https://example.com/updated.png",
                "google",
                "길동",
                "홍"));

    // when & then
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(authenticated(userAuthentication(userId)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                                {
                                                  "nickname": "수정닉",
                                                  "profileImageUrl": "https://example.com/updated.png",
                                                  "firstName": "길동",
                                                  "lastName": "홍"
                                                }
                                                """))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("수정닉"))
        .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/updated.png"))
        .andDo(
            document(
                "users-me-update",
                requestFields(
                    fieldWithPath("nickname").optional().description("닉네임"),
                    fieldWithPath("profileImageUrl").optional().description("프로필 이미지 URL"),
                    fieldWithPath("firstName").optional().description("이름"),
                    fieldWithPath("lastName").optional().description("성")),
                responseFields(
                    fieldWithPath("userId").description("사용자 ID"),
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("nickname").description("닉네임"),
                    fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                    fieldWithPath("provider").description("OAuth2 provider"),
                    fieldWithPath("firstName").description("이름"),
                    fieldWithPath("lastName").description("성"))));
  }

  @Test
  @DisplayName("인증된 사용자는 회원 탈퇴할 수 있다")
  void deleteMyAccountSuccess() throws Exception {
    // given
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    doNothing().when(userService).deleteMyAccount(any(AuthenticatedUser.class));

    // when & then
    mockMvc
        .perform(
            delete("/api/v1/users/me")
                .with(authenticated(userAuthentication(userId)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token"))
        .andDo(print())
        .andExpect(status().isNoContent())
        .andDo(document("users-me-delete"));
  }

  @Test
  @DisplayName("내 프로필 조회 요청 시 인증 토큰이 없으면 401 Unauthorized를 반환한다")
  void getMyProfileFailWithoutAuthentication() throws Exception {
    // when & then
    mockMvc.perform(get("/api/v1/users/me")).andDo(print()).andExpect(status().isUnauthorized());

    verify(userService, never()).getMyProfile(any(AuthenticatedUser.class));
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
  @DisplayName("인증된 사용자는 회원 탈퇴할 수 있다")
  void deleteMyAccountSuccess() throws Exception {
    // given
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    doNothing().when(userService).deleteMyAccount(any(AuthenticatedUser.class));

    // when & then
    mockMvc
        .perform(
            delete("/api/v1/users/me")
                .with(authenticated(userAuthentication(userId)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token"))
        .andDo(print())
        .andExpect(status().isNoContent())
        .andDo(document("users-me-delete"));
  }

  @Test
  @DisplayName("내 프로필 조회 요청 시 인증 토큰이 없으면 401 Unauthorized를 반환한다")
  void getMyProfileFailWithoutAuthentication() throws Exception {
    // when & then
    mockMvc.perform(get("/api/v1/users/me")).andDo(print()).andExpect(status().isUnauthorized());

    verify(userService, never()).getMyProfile(any(AuthenticatedUser.class));
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
