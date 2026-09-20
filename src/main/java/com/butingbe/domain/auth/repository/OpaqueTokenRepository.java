package com.butingbe.domain.auth.repository;

import com.butingbe.domain.auth.entity.OpaqueToken;
import com.butingbe.domain.auth.entity.OpaqueTokenType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpaqueTokenRepository extends JpaRepository<OpaqueToken, UUID> {

  @Query(
      """
      select token
      from OpaqueToken token
      join fetch token.user
      where token.tokenHash = :tokenHash
        and token.revokedAt is null
      """)
  Optional<OpaqueToken> findByTokenHashAndRevokedAtIsNull(@Param("tokenHash") String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from OpaqueToken token where token.user.id = :userId")
  int deleteByUserId(@Param("userId") UUID userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from OpaqueToken token
      where token.user.id = :userId
        and token.revokedAt is null
        and token.expiresAt > :now
      """)
  int deleteActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

  /** 종류를 지정해 살아 있는 토큰만 지운다. 회전 시 리프레시만 갈아끼우는 데 쓴다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from OpaqueToken token
      where token.user.id = :userId
        and token.tokenType = :tokenType
        and token.revokedAt is null
        and token.expiresAt > :now
      """)
  int deleteActiveByUserIdAndType(
      @Param("userId") UUID userId,
      @Param("tokenType") OpaqueTokenType tokenType,
      @Param("now") LocalDateTime now);
}
