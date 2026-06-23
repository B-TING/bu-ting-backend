package com.butingbe;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ButingBeApplication {

  private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";

  public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
    SpringApplication.run(ButingBeApplication.class, args);
  }
}
