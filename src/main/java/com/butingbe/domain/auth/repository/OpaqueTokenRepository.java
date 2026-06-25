package com.butingbe.domain.auth.repository;

import com.butingbe.domain.auth.entity.OpaqueToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpaqueTokenRepository extends JpaRepository<OpaqueToken, UUID> {

  @Query(
      """
      SELECT token
      FROM OpaqueToken token
      JOIN FETCH token.user
      WHERE token.tokenHash = :tokenHash
        AND token.revokedAt IS NULL
      """)
  Optional<OpaqueToken> findByTokenHashAndRevokedAtIsNull(@Param("tokenHash") String tokenHash);
}
