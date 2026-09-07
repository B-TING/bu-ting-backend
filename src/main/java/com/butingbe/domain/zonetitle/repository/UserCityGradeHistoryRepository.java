package com.butingbe.domain.zonetitle.repository;

import com.butingbe.domain.zonetitle.entity.UserCityGradeHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCityGradeHistoryRepository extends JpaRepository<UserCityGradeHistory, UUID> {

  Optional<UserCityGradeHistory> findTopByUserIdOrderByReachedAtDesc(UUID userId);
}
