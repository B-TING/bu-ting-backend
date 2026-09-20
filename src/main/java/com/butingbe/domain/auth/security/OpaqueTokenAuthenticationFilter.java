package com.butingbe.domain.auth.security;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private static final String LOCAL_PROFILE = "local";

  private final OpaqueTokenService opaqueTokenService;
  private final String adminToken;
  private final boolean adminTokenEnabled;

  public OpaqueTokenAuthenticationFilter(
      OpaqueTokenService opaqueTokenService,
      Environment environment,
      @Value("${admin.token:}") String adminToken) {
    this.opaqueTokenService = opaqueTokenService;
    this.adminToken = adminToken;
    this.adminTokenEnabled =
        environment.matchesProfiles(LOCAL_PROFILE) && StringUtils.hasText(adminToken);
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
    return adminTokenEnabled
        && MessageDigest.isEqual(
            adminToken.getBytes(StandardCharsets.UTF_8), rawToken.getBytes(StandardCharsets.UTF_8));
  }
}
