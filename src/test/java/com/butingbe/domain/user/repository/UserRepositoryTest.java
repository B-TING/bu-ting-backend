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
  @DisplayName("이메일로 사용자를 찾는다")
  void findByEmailSuccess() {
    userRepository.save(createTestUser("test@example.com", "테스터"));

    assertThat(userRepository.findByEmail("test@example.com"))
        .get()
        .extracting(User::getNickname)
        .isEqualTo("테스터");
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
