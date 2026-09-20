package com.butingbe.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.entity.OpaqueToken;
import com.butingbe.domain.auth.entity.OpaqueTokenType;
import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OpaqueTokenCleanupSchedulerTest extends AbstractContainerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 20, 4, 0);

  @Autowired private OpaqueTokenRepository opaqueTokenRepository;

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("보관 기간이 지난 만료·폐기 토큰만 지우고 나머지는 남긴다")
  void deletesOnlyExpiredOrLongRevokedTokens() {
    User user = saveUser("token-cleanup");
    OpaqueToken longExpired = save(user, "1", NOW.minusDays(30), null);
    OpaqueToken justExpired = save(user, "2", NOW.minusDays(1), null);
    OpaqueToken longRevoked = save(user, "3", NOW.plusDays(20), NOW.minusDays(30));
    OpaqueToken justRevoked = save(user, "4", NOW.plusDays(20), NOW.minusDays(1));
    OpaqueToken active = save(user, "5", NOW.plusDays(20), null);

    new OpaqueTokenCleanupScheduler(opaqueTokenRepository, 7).cleanUp(NOW);

    assertThat(opaqueTokenRepository.findAllById(List.of(longExpired.getId(), longRevoked.getId())))
        .isEmpty();
    assertThat(
            opaqueTokenRepository.findAllById(
                List.of(justExpired.getId(), justRevoked.getId(), active.getId())))
        .hasSize(3);
  }

  @Test
  @DisplayName("보관 기간 설정이 바뀌면 기준 시각도 따라간다")
  void usesConfiguredRetention() {
    OpaqueTokenRepository repository = mock(OpaqueTokenRepository.class);
    when(repository.deleteExpiredOrRevokedBefore(any())).thenReturn(3);

    new OpaqueTokenCleanupScheduler(repository, 14).cleanUp(NOW);

    ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(repository).deleteExpiredOrRevokedBefore(cutoff.capture());
    assertThat(cutoff.getValue()).isEqualTo(NOW.minusDays(14));
  }

  @Test
  @DisplayName("정리가 실패해도 예외를 밖으로 던지지 않는다")
  void swallowsFailure() {
    OpaqueTokenRepository repository = mock(OpaqueTokenRepository.class);
    when(repository.deleteExpiredOrRevokedBefore(any())).thenThrow(new IllegalStateException("db"));

    assertThatCode(() -> new OpaqueTokenCleanupScheduler(repository, 7).cleanUp(NOW))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("주기 진입점은 현재 시각으로 정리를 호출한다")
  void dailyEntryPointRuns() {
    OpaqueTokenRepository repository = mock(OpaqueTokenRepository.class);
    when(repository.deleteExpiredOrRevokedBefore(any())).thenReturn(0);

    new OpaqueTokenCleanupScheduler(repository, 7).cleanUpDaily();

    verify(repository).deleteExpiredOrRevokedBefore(any());
  }

  private OpaqueToken save(
      User user, String slug, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    OpaqueToken token =
        OpaqueToken.builder()
            .tokenHash(slug.repeat(64))
            .user(user)
            .tokenType(OpaqueTokenType.REFRESH)
            .expiresAt(expiresAt)
            .build();
    if (revokedAt != null) {
      token.revoke(revokedAt);
    }
    return opaqueTokenRepository.save(token);
  }

  private User saveUser(String slug) {
    return userRepository.save(
        User.builder()
            .email(slug + "@example.com")
            .provider("google")
            .providerId("google-" + slug)
            .name(new Name("홍", "길동"))
            .nickname(slug)
            .role(UserRole.USER)
            .build());
  }
}
