package com.butingbe.domain.auth.service;

import com.butingbe.domain.auth.entity.OpaqueToken;
import com.butingbe.domain.auth.entity.OpaqueTokenType;
import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OpaqueTokenService {

  public static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 60L * 60L;
  public static final long REFRESH_TOKEN_EXPIRES_IN_SECONDS = 60L * 60L * 24L * 30L;
  private static final String HASH_ALGORITHM = "SHA-256";
  private static final String BEARER_PREFIX = "Bearer ";

  private final OpaqueTokenRepository opaqueTokenRepository;
  private final String hashAlgorithm;
  private final SecureRandom secureRandom = new SecureRandom();

  @Autowired
  public OpaqueTokenService(OpaqueTokenRepository opaqueTokenRepository) {
    this(opaqueTokenRepository, HASH_ALGORITHM);
  }

  OpaqueTokenService(OpaqueTokenRepository opaqueTokenRepository, String hashAlgorithm) {
    this.opaqueTokenRepository = opaqueTokenRepository;
    this.hashAlgorithm = hashAlgorithm;
  }

  @Transactional
  public IssuedOpaqueToken issue(User user) {
    return issueNew(user);
  }

  @Transactional
  public IssuedOpaqueToken issue(User user, String authorization) {
    LocalDateTime now = LocalDateTime.now();
    return extractBearerToken(authorization)
        .flatMap(rawToken -> findReusableToken(user, rawToken, now))
        .orElseGet(() -> issueNew(user));
  }

  private IssuedOpaqueToken issueNew(User user) {
    LocalDateTime now = LocalDateTime.now();
    opaqueTokenRepository.deleteActiveByUserId(user.getId(), now);
    return new IssuedOpaqueToken(
        save(user, OpaqueTokenType.ACCESS, now),
        "Bearer",
        ACCESS_TOKEN_EXPIRES_IN_SECONDS,
        now.plusSeconds(ACCESS_TOKEN_EXPIRES_IN_SECONDS),
        save(user, OpaqueTokenType.REFRESH, now),
        REFRESH_TOKEN_EXPIRES_IN_SECONDS);
  }

  /**
   * 리프레시 토큰으로 새 액세스 토큰을 발급한다.
   *
   * <p>쓰인 리프레시는 즉시 폐기하고 새것을 함께 내준다(회전). 탈취된 토큰이 계속 쓰이는 것을 막고, 이미 쓰인 토큰이 다시 오면 거부된다.
   */
  @Transactional
  public IssuedOpaqueToken refresh(String rawRefreshToken) {
    LocalDateTime now = LocalDateTime.now();
    OpaqueToken refreshToken =
        opaqueTokenRepository
            .findByTokenHashAndRevokedAtIsNull(hash(rawRefreshToken))
            .filter(token -> token.isActive(now))
            .filter(token -> token.getTokenType() == OpaqueTokenType.REFRESH)
            .orElseThrow(UnauthenticatedException::new);

    User user = refreshToken.getUser();
    // 액세스는 새로 발급하고, 쓰인 리프레시는 새것으로 갈아끼운다.
    opaqueTokenRepository.deleteActiveByUserIdAndType(user.getId(), OpaqueTokenType.ACCESS, now);
    opaqueTokenRepository.deleteActiveByUserIdAndType(user.getId(), OpaqueTokenType.REFRESH, now);

    return new IssuedOpaqueToken(
        save(user, OpaqueTokenType.ACCESS, now),
        "Bearer",
        ACCESS_TOKEN_EXPIRES_IN_SECONDS,
        now.plusSeconds(ACCESS_TOKEN_EXPIRES_IN_SECONDS),
        save(user, OpaqueTokenType.REFRESH, now),
        REFRESH_TOKEN_EXPIRES_IN_SECONDS);
  }

  /** 토큰을 만들어 저장하고 원문을 돌려준다. 저장되는 것은 해시뿐이다. */
  private String save(User user, OpaqueTokenType type, LocalDateTime now) {
    String rawToken = generateToken();
    long lifetime =
        type == OpaqueTokenType.REFRESH
            ? REFRESH_TOKEN_EXPIRES_IN_SECONDS
            : ACCESS_TOKEN_EXPIRES_IN_SECONDS;
    opaqueTokenRepository.save(
        OpaqueToken.builder()
            .tokenHash(hash(rawToken))
            .user(user)
            .tokenType(type)
            .expiresAt(now.plusSeconds(lifetime))
            .build());
    return rawToken;
  }

  @Transactional(readOnly = true)
  public Optional<User> authenticate(String rawToken) {
    LocalDateTime now = LocalDateTime.now();
    return opaqueTokenRepository
        .findByTokenHashAndRevokedAtIsNull(hash(rawToken))
        .filter(token -> token.isActive(now))
        // 리프레시로 API를 호출할 수 없어야 한다. 수명이 길어 탈취 시 피해가 크다.
        .filter(token -> token.getTokenType() == OpaqueTokenType.ACCESS)
        .map(OpaqueToken::getUser);
  }

  private Optional<IssuedOpaqueToken> findReusableToken(
      User user, String rawToken, LocalDateTime now) {
    return opaqueTokenRepository
        .findByTokenHashAndRevokedAtIsNull(hash(rawToken))
        .filter(token -> token.isActive(now))
        .filter(token -> token.getTokenType() == OpaqueTokenType.ACCESS)
        .filter(token -> belongsTo(token, user))
        .map(token -> issueExisting(user, rawToken, token.getExpiresAt(), now));
  }

  private boolean belongsTo(OpaqueToken token, User user) {
    return Objects.equals(token.getUser().getId(), user.getId());
  }

  /**
   * 살아 있는 액세스 토큰은 그대로 쓰고 리프레시만 새로 내준다.
   *
   * <p>앱이 액세스 토큰을 들고 다시 로그인해도 토큰이 갈리지 않게 하려는 기존 동작을 유지하면서, 리프레시가 없던 이전 발급분에도 리프레시를 쥐여 준다.
   */
  private IssuedOpaqueToken issueExisting(
      User user, String rawToken, LocalDateTime expiresAt, LocalDateTime now) {
    long expiresIn = Math.max(0L, Duration.between(now, expiresAt).toSeconds());
    opaqueTokenRepository.deleteActiveByUserIdAndType(user.getId(), OpaqueTokenType.REFRESH, now);
    return new IssuedOpaqueToken(
        rawToken,
        "Bearer",
        expiresIn,
        expiresAt,
        save(user, OpaqueTokenType.REFRESH, now),
        REFRESH_TOKEN_EXPIRES_IN_SECONDS);
  }

  private Optional<String> extractBearerToken(String authorization) {
    if (!StringUtils.hasText(authorization)
        || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return Optional.empty();
    }

    String rawToken = authorization.substring(BEARER_PREFIX.length()).trim();
    return StringUtils.hasText(rawToken) ? Optional.of(rawToken) : Optional.empty();
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (byte value : hashed) {
        builder.append("%02x".formatted(value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available.", e);
    }
  }

  public record IssuedOpaqueToken(
      String accessToken,
      String tokenType,
      long expiresIn,
      LocalDateTime expiresAt,
      String refreshToken,
      long refreshExpiresIn) {}
}
