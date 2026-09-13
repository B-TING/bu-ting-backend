package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.CommentPageResDto;
import com.butingbe.domain.zoneevent.dto.response.CommentResDto;
import com.butingbe.domain.zoneevent.dto.response.LikeResDto;
import com.butingbe.domain.zoneevent.dto.response.ReportResDto;
import com.butingbe.domain.zoneevent.service.ZoneEventSocialService;
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
class ZoneEventSocialControllerTest {

  private static final UUID PID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID CID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private ZoneEventSocialService socialService;
  @InjectMocks private ZoneEventSocialController controller;

  private MockMvc mockMvc;

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
  @DisplayName("좋아요 201 / 취소 204")
  void likeAndUnlike() throws Exception {
    when(socialService.like(any(), eq(PID)))
        .thenReturn(
            new LikeResDto(UUID.randomUUID().toString(), PID.toString(), OffsetDateTime.now(), 1));

    mockMvc
        .perform(post("/zone-event-participations/{id}/likes", PID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.likeCount").value(1));
    mockMvc
        .perform(delete("/zone-event-participations/{id}/likes", PID))
        .andExpect(status().isNoContent());
    verify(socialService).unlike(any(), eq(PID));
  }

  @Test
  @DisplayName("댓글 작성 201 / 조회 200 / 수정 200 / 삭제 204")
  void commentCrud() throws Exception {
    when(socialService.addComment(any(), eq(PID), eq("댓글"))).thenReturn(comment());
    when(socialService.getComments(eq(PID), any(), any()))
        .thenReturn(new CommentPageResDto(List.of(comment()), null, false));
    when(socialService.editComment(any(), eq(CID), eq("수정"))).thenReturn(comment());

    mockMvc
        .perform(
            post("/zone-event-participations/{id}/comments", PID)
                .contentType("application/json")
                .content("{\"content\":\"댓글\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/zone-event-participations/{id}/comments", PID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].commentId").value(CID.toString()));
    mockMvc
        .perform(
            patch("/zone-event-participations/{id}/comments/{cid}", PID, CID)
                .contentType("application/json")
                .content("{\"content\":\"수정\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/zone-event-participations/{id}/comments/{cid}", PID, CID))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("댓글 내용이 없으면 400이다")
  void commentBlankRejected() throws Exception {
    mockMvc
        .perform(
            post("/zone-event-participations/{id}/comments", PID)
                .contentType("application/json")
                .content("{\"content\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("신고 201")
  void report() throws Exception {
    when(socialService.report(any(), eq(PID), eq("SPAM"), any()))
        .thenReturn(
            new ReportResDto(UUID.randomUUID().toString(), PID.toString(), OffsetDateTime.now()));

    mockMvc
        .perform(
            post("/zone-event-participations/{id}/reports", PID)
                .contentType("application/json")
                .content("{\"reasonCode\":\"SPAM\",\"memo\":\"숙소\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.participationId").value(PID.toString()));
  }

  private CommentResDto comment() {
    return new CommentResDto(
        CID.toString(),
        PID.toString(),
        USER_ID.toString(),
        "author",
        null,
        null,
        "댓글",
        OffsetDateTime.now(),
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
        return new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
      }
    };
  }
}
