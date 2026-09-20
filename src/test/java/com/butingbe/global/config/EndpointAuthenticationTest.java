package com.butingbe.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 매핑된 모든 엔드포인트를 훑어, 공개 목록에 없는 경로는 비인증 호출에서 401 이 되는지 본다.
 *
 * <p>엔드포인트를 손으로 나열하지 않는다. 새 API 가 생기면 이 테스트가 자동으로 포함하므로, 인증을 빠뜨린 채 머지되는 일을 막는다.
 */
class EndpointAuthenticationTest extends AbstractContainerTest {

  /**
   * 비인증으로 열려 있어야 하는 경로. {@code SecurityConfig} 의 공개 목록과 같은 뜻이며, 여기 없는 경로가 401 이 아니면 테스트가 깨진다.
   *
   * <p>경로 변수는 매핑 패턴 그대로 적는다.
   */
  private static final Set<String> PUBLIC_ENDPOINTS =
      Set.of(
          "POST /api/v1/auth/oauth/login",
          "POST /api/v1/auth/refresh",
          "GET /api/v1/places",
          "GET /api/v1/places/search",
          "GET /api/v1/places/location",
          "GET /api/v1/places/festivals",
          "GET /api/v1/places/reviews",
          "GET /api/v1/places/travel-records",
          "GET /api/v1/places/{contentId}/detail",
          "GET /api/v1/storage-locations",
          "GET /api/v1/chat/rooms/zone",
          "GET /api/v1/travel/team/invites/verify",
          "GET /api/v1/zone-titles",
          "GET /api/v1/zone-event-rounds/current",
          "GET /api/v1/zone-event-rounds/{roundId}/album",
          "GET /api/v1/zone-event-participations/{participationId}/comments",
          "GET /api/v1/travel-records",
          "GET /api/v1/travel-records/{travelRecordId}",
          "GET /api/v1/travel-records/{travelRecordId}/comments",
          "GET /api/v1/zone-events/active",
          "GET /api/v1/zone-events/{eventId}",
          "GET /api/v1/zone-events/{eventId}/album",
          "GET /api/v1/zones/{zoneId}/album");

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired private UserRepository userRepository;

  @Autowired private OpaqueTokenService opaqueTokenService;

  // 액추에이터가 같은 타입의 매핑 빈을 하나 더 등록한다. 도메인 컨트롤러를 들고 있는 쪽을 이름으로 집는다.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("공개 목록에 없는 엔드포인트는 비인증 호출에서 401을 돌려준다")
  void everyNonPublicEndpointRejectsAnonymousRequests() throws Exception {
    List<String> leaked = new ArrayList<>();
    Set<String> endpoints = new TreeSet<>(mappedEndpoints());

    // 매핑을 못 읽으면 아무것도 검사하지 않고 통과한다. 그 상태를 성공으로 오해하지 않도록 먼저 막는다.
    assertThat(endpoints).describedAs("매핑된 /api/v1 엔드포인트").hasSizeGreaterThan(150);

    for (String endpoint : endpoints) {
      if (PUBLIC_ENDPOINTS.contains(endpoint)) {
        continue;
      }
      MockHttpServletResponse response = callAnonymously(endpoint);
      if (response.getStatus() != HttpStatus.UNAUTHORIZED.value()) {
        leaked.add(endpoint + " -> " + response.getStatus());
      }
    }

    assertThat(leaked)
        .describedAs("인증 없이 401 이 아닌 응답을 내려준 엔드포인트. 공개여야 한다면 SecurityConfig 와 이 테스트의 목록에 함께 추가한다.")
        .isEmpty();
  }

  @Test
  @DisplayName("공개 목록의 엔드포인트는 비인증 호출에서도 401이 아니다")
  void publicEndpointsStayReachableWithoutAuthentication() throws Exception {
    List<String> blocked = new ArrayList<>();

    for (String endpoint : PUBLIC_ENDPOINTS) {
      MockHttpServletResponse response = callAnonymously(endpoint);
      if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
        blocked.add(endpoint);
      }
    }

    assertThat(blocked).describedAs("비로그인으로 열려 있어야 하는데 401 이 나온 엔드포인트").isEmpty();
  }

  @Test
  @DisplayName("필터에서 막힌 401도 ApiResponse 형식으로 내려간다")
  void unauthenticatedResponseUsesApiResponseShape() throws Exception {
    MockHttpServletResponse response = callAnonymously("GET /api/v1/users/me");

    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(response.getContentAsString()).contains("\"success\":false").contains("\"message\"");
  }

  @Test
  @DisplayName("STOMP 핸드셰이크는 인증 없이 통과한다")
  void stompHandshakeIsNotBlockedByHttpAuthentication() throws Exception {
    // 토큰은 핸드셰이크 헤더가 아니라 STOMP CONNECT 프레임으로 온다. 여기서 401 이 나면 연결 자체가 끊긴다.
    // 업그레이드 헤더 없이 부르므로 성공 상태는 아니지만, 401 만 아니면 된다.
    MockHttpServletResponse response =
        mockMvc.perform(MockMvcRequestBuilders.get("/ws-stomp")).andReturn().getResponse();

    assertThat(response.getStatus()).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  @DisplayName("운영자가 아닌 사용자가 admin API를 부르면 403을 ApiResponse 형식으로 돌려준다")
  void nonOperatorGetsForbiddenInApiResponseShape() throws Exception {
    User user = userRepository.save(createUser("endpoint-auth@example.com", "endpoint-auth"));
    String accessToken = opaqueTokenService.issue(user).accessToken();

    MockHttpServletResponse response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/v1/admin/zone-events")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(response.getContentAsString()).contains("\"success\":false").contains("\"message\"");
  }

  private User createUser(String email, String nickname) {
    return User.builder()
        .email(email)
        .nickname(nickname)
        .name(new Name("홍", "길동"))
        .role(UserRole.USER)
        .build();
  }

  /** 매핑 정보를 "METHOD /path" 목록으로 편다. 메서드가 여러 개인 매핑은 각각 펼친다. */
  private List<String> mappedEndpoints() {
    List<String> endpoints = new ArrayList<>();
    handlerMapping
        .getHandlerMethods()
        .forEach(
            (info, handlerMethod) -> {
              for (String pattern : patternsOf(info)) {
                if (!pattern.startsWith("/api/v1")) {
                  continue;
                }
                for (org.springframework.web.bind.annotation.RequestMethod method :
                    info.getMethodsCondition().getMethods()) {
                  endpoints.add(method.name() + " " + pattern);
                }
              }
            });
    return endpoints;
  }

  private Set<String> patternsOf(RequestMappingInfo info) {
    if (info.getPathPatternsCondition() != null) {
      return info.getPathPatternsCondition().getPatternValues();
    }
    PatternsRequestCondition patterns = info.getPatternsCondition();
    return patterns == null ? Set.of() : patterns.getPatterns();
  }

  /** 경로 변수는 아무 값으로나 채운다. 인증 이전 단계에서 걸리는지만 보므로 실제 리소스일 필요가 없다. */
  private MockHttpServletResponse callAnonymously(String endpoint) throws Exception {
    String[] parts = endpoint.split(" ", 2);
    String path = parts[1].replaceAll("\\{[^/]+}", "00000000-0000-0000-0000-000000000000");
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.request(HttpMethod.valueOf(parts[0]), path)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{}");
    return mockMvc.perform(request).andReturn().getResponse();
  }
}
