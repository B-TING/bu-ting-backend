package com.butingbe.domain.auth.repository;

import com.butingbe.domain.auth.entity.OpaqueToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpaqueTokenRepository extends JpaRepository<OpaqueToken, UUID> {

  Optional<OpaqueToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
