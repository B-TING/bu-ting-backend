package com.butingbe.domain.notification.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** FCM이 꺼져 있을 때(기본)의 푸시 전송: 로그만 남긴다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "push.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

  @Override
  public int send(List<String> fcmTokens, String title, String body) {
    log.info("[push] title='{}' recipients={} (logging sender)", title, fcmTokens.size());
    return fcmTokens.size();
  }
}
