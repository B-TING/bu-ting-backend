package com.butingbe.domain.auth.repository;

import com.butingbe.domain.auth.entity.OpaqueToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpaqueTokenRepository extends JpaRepository<OpaqueToken, UUID> {

  Optional<OpaqueToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from OpaqueToken token where token.user.id = :userId")
  int deleteByUserId(@Param("userId") UUID userId);
}
