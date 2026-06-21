package com.butingbe.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // 💡 Spring 대신 Mockito 환경만 빠르게 실행!
@DisplayName("UserServiceImpl 단위 테스트")
class UserServiceImplTest {

  @InjectMocks private UserServiceImpl userService; // Mock 객체들이 주입될 대상

  @Mock private UserRepository userRepository; // 가짜 객체(Mock)로 대체

  @Nested
  @DisplayName("signUp() - 회원가입 테스트")
  class SignUpTest {

    @Test
    @DisplayName("중복되지 않은 이메일이면 회원가입 로직이 정상 수행되고 save()가 호출된다")
    void signUpSuccess() {
      // given
      SignUpReqDto request = new SignUpReqDto("test@example.com", "부팅이", "GOOGLE", "id", "길동", "홍");
      given(userRepository.existsByEmail(request.email())).willReturn(false); // 가짜 행동 정의

      // when
      userService.signUp(request);

      // then
      verify(userRepository).save(any(User.class)); // 실제로 save가 호출되었는지 행위 검증
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 save()를 호출하지 않고 DuplicateResourceException이 발생한다")
    void signUpFailWithDuplicateEmail() {
      // given
      SignUpReqDto request =
          new SignUpReqDto("duplicate@example.com", "새유저", null, null, "철수", "김");
      given(userRepository.existsByEmail(request.email())).willReturn(true);

      // when & then
      assertThatThrownBy(() -> userService.signUp(request))
          .isInstanceOf(DuplicateResourceException.class);

      verify(userRepository, never()).save(any(User.class)); // 예외가 터졌으므로 save는 절대 호출되면 안 됨
    }
  }

  @Nested
  @DisplayName("signIn() - 로그인 테스트")
  class SignInTest {

    @Test
    @DisplayName("존재하는 유저 이메일이면 유저 정보를 DTO로 정상 반환한다")
    void signInSuccess() {
      // given
      String email = "login@example.com";
      User user = User.builder().email(email).nickname("로그인유저").build();
      given(userRepository.findByEmail(email)).willReturn(Optional.of(user));

      // when
      UserResDto response = userService.signIn(email);

      // then
      assertThat(response).isNotNull();
      assertThat(response.email()).isEqualTo(email);
      assertThat(response.nickname()).isEqualTo("로그인유저");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null"})
    @DisplayName("유효하지 않은 이메일 파라미터면 리포지토리를 조회하지 않고 UnauthenticatedException이 발생한다")
    void signInFailWithInvalidEmailParam(String invalidEmail) {
      // when & then
      assertThatThrownBy(() -> userService.signIn(invalidEmail))
          .isInstanceOf(UnauthenticatedException.class);

      // 가짜 파라미터 필터링 단계에서 걸려야 하므로 DB 조회(Repository)까지 가지도 않아야 함
      verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 UnauthenticatedException이 발생한다")
    void signInFailWithNotFoundUser() {
      // given
      String notFoundEmail = "nobody@example.com";
      given(userRepository.findByEmail(notFoundEmail)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> userService.signIn(notFoundEmail))
          .isInstanceOf(UnauthenticatedException.class);
    }
  }
}
