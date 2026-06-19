package com.butingbe.domain.user.repository;

import com.butingbe.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByProviderAndProviderId(String provider, String providerId);

  boolean existsByEmail(String email);
}
