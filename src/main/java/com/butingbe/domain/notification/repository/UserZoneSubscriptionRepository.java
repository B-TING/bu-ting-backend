package com.butingbe.domain.notification.repository;

import com.butingbe.domain.notification.entity.UserZoneSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserZoneSubscriptionRepository extends JpaRepository<UserZoneSubscription, UUID> {

  List<UserZoneSubscription> findByUserId(UUID userId);

  List<UserZoneSubscription> findByZoneId(String zoneId);

  void deleteByUserId(UUID userId);
}
