package com.butingbe.domain.user.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.service.UserService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  /** 회원가입 API */
  @PostMapping("/signup")
  public ResponseEntity<Void> signUp(
      @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
      @RequestBody @Valid SignUpReqDto request) {
    if (authenticatedUser == null) {
      throw new UnauthenticatedException();
    }

    userService.signUp(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** 로그인(조회) API */
  @GetMapping("/signin")
  public ResponseEntity<UserResDto> signIn(@RequestParam String email) {
    UserResDto response = userService.signIn(email);
    return ResponseEntity.ok(response);
  }
}
