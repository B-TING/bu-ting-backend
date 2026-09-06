package com.butingbe.domain.notification.repository;

import com.butingbe.domain.notification.entity.NotificationType;
import com.butingbe.domain.notification.entity.UserNotificationPreference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPreferenceRepository
    extends JpaRepository<UserNotificationPreference, UUID> {

  List<UserNotificationPreference> findByUserId(UUID userId);

  Optional<UserNotificationPreference> findByUserIdAndNotificationType(
      UUID userId, NotificationType type);
}
