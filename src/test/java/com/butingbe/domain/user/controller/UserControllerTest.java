package com.butingbe.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.service.UserService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  private MockMvc mockMvc;

  @Mock private UserService userService;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mockMvc =
        MockMvcBuilders.standaloneSetup(new UserController(userService))
            .setControllerAdvice(new GlobalExceptionHandler())
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

  // ==========================================
  // 👤 SIGN UP (회원가입) TEST
  // ==========================================

  @Test
  @DisplayName("올바른 회원가입 데이터가 JSON 형태로 들어오면 201 Created를 반환한다")
  void signUpSuccess() throws Exception {
    // given
    doNothing().when(userService).signUp(any(SignUpReqDto.class));

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
        .andExpect(status().isCreated());
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
        .andExpect(jsonPath("$.nickname").value("홍길동"));
  }
}
