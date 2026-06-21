package com.butingbe.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.support.AbstractContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserRepositoryTest extends AbstractContainerTest {

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("이메일로 사용자가 존재하는지 정확하게 확인한다")
  void existsByEmailSuccess() {
    // given
    User user = createTestUser("test@example.com", "테스터");
    userRepository.save(user);

    // when
    boolean exists = userRepository.existsByEmail("test@example.com");

    // then
    assertThat(exists).isTrue();
  }

  private User createTestUser(String email, String nickname) {
    return User.builder()
        .email(email)
        .nickname(nickname)
        .name(new Name("홍", "길동"))
        .role(UserRole.USER)
        .build();
  }
}
