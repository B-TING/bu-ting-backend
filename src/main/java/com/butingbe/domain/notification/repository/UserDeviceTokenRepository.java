package com.butingbe.domain.notification.repository;

import com.butingbe.domain.notification.entity.UserDeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

  Optional<UserDeviceToken> findByFcmToken(String fcmToken);

  void deleteByFcmTokenAndUserId(String fcmToken, UUID userId);

  List<UserDeviceToken> findByUserIdIn(Collection<UUID> userIds);
}
