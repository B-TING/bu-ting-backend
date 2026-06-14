package com.butingbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 임시로 시큐리티 자동 설정을 꺼두는 방법
@SpringBootApplication(
    exclude = {
      SecurityAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class // 임시 비밀번호 콘솔 노출 방
    })
@EnableJpaAuditing
public class ButingBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(ButingBeApplication.class, args);
  }
}
