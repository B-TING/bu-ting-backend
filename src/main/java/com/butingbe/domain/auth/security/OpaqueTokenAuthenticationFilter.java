package com.butingbe.domain.auth.security;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final OpaqueTokenService opaqueTokenService;
  private final String adminToken;

  public OpaqueTokenAuthenticationFilter(
      OpaqueTokenService opaqueTokenService,
      @Value("${admin.token:${ADMIN_TOKEN:}}") String adminToken) {
    this.opaqueTokenService = opaqueTokenService;
    this.adminToken = adminToken;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authorization != null
        && authorization.startsWith(BEARER_PREFIX)
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      String rawToken = authorization.substring(BEARER_PREFIX.length()).trim();
      if (isAdminToken(rawToken)) {
        authenticate(AuthenticatedUser.developmentAdmin());
      } else {
        opaqueTokenService.authenticate(rawToken).ifPresent(this::authenticate);
      }
    }

    filterChain.doFilter(request, response);
  }

  private void authenticate(User user) {
    authenticate(AuthenticatedUser.from(user));
  }

  private void authenticate(AuthenticatedUser principal) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private boolean isAdminToken(String rawToken) {
    return StringUtils.hasText(adminToken) && adminToken.equals(rawToken);
  }
}
