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

  /** 폐기 여부와 무관하게 찾는다. 이미 회전된 리프레시가 다시 오는 것을 감지하려면 폐기된 행도 봐야 한다. */
  @Query(
      """
      select token
      from OpaqueToken token
      join fetch token.user
      where token.tokenHash = :tokenHash
      """)
  Optional<OpaqueToken> findByTokenHash(@Param("tokenHash") String tokenHash);

  /** 살아 있는 토큰을 종류 구분 없이 모두 폐기한다. 회전과 재사용 감지에 쓴다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update OpaqueToken token
      set token.revokedAt = :now
      where token.user.id = :userId
        and token.revokedAt is null
        and token.expiresAt > :now
      """)
  int revokeActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

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

  /** 기준 시각 이전에 만료됐거나 폐기된 행을 지운다. 정리 스케줄러가 쓴다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from OpaqueToken token
      where token.expiresAt < :cutoff
         or (token.revokedAt is not null and token.revokedAt < :cutoff)
      """)
  int deleteExpiredOrRevokedBefore(@Param("cutoff") LocalDateTime cutoff);
}
