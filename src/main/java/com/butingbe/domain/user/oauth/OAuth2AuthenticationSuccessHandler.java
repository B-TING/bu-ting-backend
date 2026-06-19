package com.butingbe.domain.user.oauth;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.auth.service.OpaqueTokenService.IssuedOpaqueToken;
import com.butingbe.domain.user.dto.response.OAuth2LoginResDto;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final OpaqueTokenService opaqueTokenService;

  @Override
  @Transactional(readOnly = true)
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
    Object userIdAttribute =
        principal.getAttributes().get(CustomOAuth2UserService.USER_ID_ATTRIBUTE);
    UUID userId = UUID.fromString(String.valueOf(userIdAttribute));
    User user = userRepository.findById(userId).orElseThrow();
    IssuedOpaqueToken issuedToken = opaqueTokenService.issue(user);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write(
            toJson(
                OAuth2LoginResDto.from(
                    user,
                    issuedToken.accessToken(),
                    issuedToken.tokenType(),
                    issuedToken.expiresIn())));
  }

  private String toJson(OAuth2LoginResDto data) {
    return """
        {"success":true,"message":"OAuth2 login succeeded.","data":{"userId":"%s","email":%s,"nickname":"%s","provider":"%s","loggedIn":true,"emailRequired":%s,"accessToken":"%s","tokenType":"%s","expiresIn":%d}}\
        """
        .formatted(
            escape(data.userId()),
            nullableStringJson(data.email()),
            escape(data.nickname()),
            escape(data.provider()),
            data.emailRequired(),
            escape(data.accessToken()),
            escape(data.tokenType()),
            data.expiresIn());
  }

  private String nullableStringJson(String value) {
    return value == null ? "null" : "\"" + escape(value) + "\"";
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
}
