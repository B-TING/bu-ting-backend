package com.butingbe.domain.user.repository;

import com.butingbe.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByProviderAndProviderId(String provider, String providerId);

  boolean existsByEmail(String email);

  /** 관리자 검색용: 닉네임 또는 이메일에 keyword가 포함된 유저. */
  List<User> findByNicknameContainingIgnoreCaseOrEmailContainingIgnoreCase(
      String nickname, String email);
}
