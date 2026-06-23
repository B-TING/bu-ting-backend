package com.butingbe.domain.auth.service;

import com.butingbe.domain.auth.entity.OpaqueToken;
import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.user.entity.User;
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

  public static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 60L * 60L * 24L * 14L;
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
    String rawToken = generateToken();
    LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ACCESS_TOKEN_EXPIRES_IN_SECONDS);

    opaqueTokenRepository.save(
        OpaqueToken.builder().tokenHash(hash(rawToken)).user(user).expiresAt(expiresAt).build());

    return new IssuedOpaqueToken(rawToken, "Bearer", ACCESS_TOKEN_EXPIRES_IN_SECONDS, expiresAt);
  }

  @Transactional(readOnly = true)
  public Optional<User> authenticate(String rawToken) {
    LocalDateTime now = LocalDateTime.now();
    return opaqueTokenRepository
        .findByTokenHashAndRevokedAtIsNull(hash(rawToken))
        .filter(token -> token.isActive(now))
        .map(OpaqueToken::getUser);
  }

  private Optional<IssuedOpaqueToken> findReusableToken(
      User user, String rawToken, LocalDateTime now) {
    return opaqueTokenRepository
        .findByTokenHashAndRevokedAtIsNull(hash(rawToken))
        .filter(token -> token.isActive(now))
        .filter(token -> belongsTo(token, user))
        .map(token -> issueExisting(rawToken, token.getExpiresAt(), now));
  }

  private boolean belongsTo(OpaqueToken token, User user) {
    return Objects.equals(token.getUser().getId(), user.getId());
  }

  private IssuedOpaqueToken issueExisting(
      String rawToken, LocalDateTime expiresAt, LocalDateTime now) {
    long expiresIn = Math.max(0L, Duration.between(now, expiresAt).toSeconds());
    return new IssuedOpaqueToken(rawToken, "Bearer", expiresIn, expiresAt);
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
      String accessToken, String tokenType, long expiresIn, LocalDateTime expiresAt) {}
}
