package com.butingbe.domain.user.controller;

import com.butingbe.domain.user.dto.request.SignUpReqDto;
import com.butingbe.domain.user.dto.response.UserResDto;
import com.butingbe.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  /** 회원가입 API */
  @PostMapping("/signup")
  public ResponseEntity<Void> signUp(@RequestBody @Valid SignUpReqDto request) {
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
