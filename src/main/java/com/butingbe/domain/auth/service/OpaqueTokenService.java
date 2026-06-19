package com.butingbe.domain.auth.service;

import com.butingbe.domain.auth.entity.OpaqueToken;
import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpaqueTokenService {

  public static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 60L * 60L * 24L * 14L;
  private static final String HASH_ALGORITHM = "SHA-256";

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
