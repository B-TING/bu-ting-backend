package com.butingbe.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.entity.OpaqueToken;
import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.butingbe.support.AbstractContainerTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 재사용 감지는 거부(예외)와 폐기(쓰기)가 한 호출에서 같이 일어난다. 테스트 트랜잭션 안에서 돌리면 롤백 규칙이 가려지므로, 이 클래스만 실제 커밋되는 상태로 확인한다.
 */
class OpaqueTokenReuseDetectionTest extends AbstractContainerTest {

  @Autowired private OpaqueTokenService opaqueTokenService;

  @Autowired private OpaqueTokenRepository opaqueTokenRepository;

  @Autowired private UserRepository userRepository;

  private User user;

  @AfterEach
  void cleanUp() {
    if (user != null) {
      opaqueTokenRepository.deleteAll(tokensOf(user));
      userRepository.deleteById(user.getId());
    }
  }

  @Test
  @DisplayName("회전된 리프레시가 다시 오면 거부하고 그 사용자의 살아 있는 토큰을 모두 끊는다")
  void reuseRevokesEveryActiveToken() {
    user = saveUser();

    OpaqueTokenService.IssuedOpaqueToken issued = opaqueTokenService.issue(user);
    OpaqueTokenService.IssuedOpaqueToken rotated =
        opaqueTokenService.refresh(issued.refreshToken());

    // 정상 회전분은 이 시점까지는 쓸 수 있다.
    assertThat(opaqueTokenService.authenticate(rotated.accessToken())).isNotEmpty();

    // 탈취된 옛 리프레시를 다시 쓰면 거부된다.
    assertThatThrownBy(() -> opaqueTokenService.refresh(issued.refreshToken()))
        .isInstanceOf(UnauthenticatedException.class);

    // 거부로 끝나지 않고, 공격자가 이미 받아 갔을 수 있는 새 토큰까지 함께 끊긴다.
    assertThat(opaqueTokenService.authenticate(rotated.accessToken())).isEmpty();
    assertThatThrownBy(() -> opaqueTokenService.refresh(rotated.refreshToken()))
        .isInstanceOf(UnauthenticatedException.class);
    assertThat(tokensOf(user))
        .isNotEmpty()
        .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
  }

  private List<OpaqueToken> tokensOf(User owner) {
    return opaqueTokenRepository.findAll().stream()
        .filter(token -> owner.getId().equals(token.getUser().getId()))
        .toList();
  }

  private User saveUser() {
    return userRepository.save(
        User.builder()
            .email("refresh-reuse@example.com")
            .provider("google")
            .providerId("google-refresh-reuse")
            .name(new Name("홍", "길동"))
            .nickname("refresh-reuse")
            .role(UserRole.USER)
            .build());
  }
}
