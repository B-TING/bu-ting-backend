package com.butingbe.domain.notification.repository;

import com.butingbe.domain.notification.entity.PushNotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushNotificationLogRepository extends JpaRepository<PushNotificationLog, UUID> {}
