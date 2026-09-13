package com.butingbe.domain.notification.service;

import java.util.List;

/**
 * 푸시 전송 포트.
 *
 * <p>기본 구현은 로그만 남긴다({@link LoggingPushSender}). 실제 FCM 어댑터(firebase-admin)는 자격증명이 준비되면 이 포트 뒤에
 * {@code push.fcm.enabled} 플래그로 끼운다 — route 도메인의 provider 패턴과 같다. 지금은 외부 SDK 의존성과 테스트 불가 코드를 들이지
 * 않는다.
 */
public interface PushSender {

  /** 토큰들에 알림을 보낸다. 실패 없이 전달을 시도하고, 전송한 수를 돌려준다. */
  int send(List<String> fcmTokens, String title, String body);
}
